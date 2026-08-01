# Storage API contract (generic storage port)

This is the **frozen specification** of the `src/api` surface for the generic storage port. It carries only the *what*, not the *why*.

## Rules for agents (not up for discussion)

The campaign is **big-bang**: `src/main` does not compile from the start of the work until the last wave finishes. You therefore have **no** compiler feedback, and this file replaces it.

1. **Do not deviate from the signatures below.** Not the name, not a parameter type, not the order.
2. **Missing a method? Stop and report.** Do not invent one. Every invented method is an integration conflict that costs more than your stopping.
3. **Do not touch files outside your list.** Packages are disjoint between agents.
4. **The old API (`IAEStack`, `IAEItemStack`, `IAEFluidStack`, `IStorageChannel`, `IMEInventory`, `IItemList`, `IStorageHelper.registerStorageChannel`) is being deleted.** Do not write new code against it and do not try to preserve compatibility — old-world and old-addon compatibility is deliberately broken.
5. **Wave 0 is `src/api`.** It compiles independently (`gradlew compileApiJava`) and is the only real gate in the whole campaign. `src/main` is guaranteed broken at that point — this is normal and expected.
6. **DO NOT CUT ANY MECHANIC.** This is a new API and new capabilities, **not** the removal of old ones. "Mirror AE2-original" governs **the shape of types and API**, and is never a licence to drop a feature. If upstream lacks a mechanism that AE2UD has, the mechanism stays and you deliberately diverge from upstream. If you cannot see how to preserve a behaviour in the new model — **stop and report**, do not delete. This has already been violated twice (Sticky Card, crafting-CPU push updates) and both had to be restored. Exactly one exception: save compatibility (rule 4).

## Adapting 1.20 → 1.12.2

The type model is taken verbatim from AE2-original (`AE2-original` @ `45f31551`), but the modern platform underneath it does not exist here. Correspondences:

| AE2-original (1.20+)                 | AE2UD (1.12.2)                                                            |
| ------------------------------------ | ------------------------------------------------------------------------- |
| `Codec` / `MapCodec` / `StreamCodec` | **none** → explicit `writeToNBT`/`fromTag` + `writeToPacket`/`fromPacket`  |
| `ValueInput` / `ValueOutput`         | `NBTTagCompound`                                                          |
| `RegistryFriendlyByteBuf`            | `ByteBuf` (netty); methods throw `IOException`                            |
| `Registry<AEKeyType>` (Mojang)       | Forge registry: `RegistryBuilder` + `IForgeRegistryEntry.Impl`            |
| `ResourceKey` / `Identifier`         | `ResourceLocation`                                                        |
| `Component`                          | `ITextComponent`                                                          |
| `ServerLevel` / `Level`              | `World`                                                                   |
| `BlockEntity`                        | `TileEntity`                                                              |
| `Direction`                          | `EnumFacing`                                                              |
| `DataComponentType` / `TagKey`       | **none** → the corresponding `AEKey` methods are not ported (see "Out of scope") |

Language level: Java 17 syntax via Jabel, compiled to bytecode 8. Records and `var` **are allowed** — they are already in the codebase (`appeng\core\localization\Tooltips.java:96`, `appeng\parts\PartPlacement.java:128`). So are lambdas and pattern-matching `instanceof`. Records need `@Desugar` (see §8).

---

## 1. `appeng.api.stacks`

### 1.1 `AEKey`

`src\api\java\appeng\api\stacks\AEKey.java`

The immutable key describing *what* is stored, **without an amount**. Replaces `IAEStack`/`IAEItemStack`/`IAEFluidStack`. Implementations must be immutable with correct `equals`/`hashCode`.

```java
public abstract class AEKey {
    /** NBT field the type id is written to. */
    public static final String TYPE_FIELD = "#t";

    // --- type identity ---
    public abstract AEKeyType getType();
    public abstract ResourceLocation getId();          // id of the key itself, e.g. minecraft:stone
    public abstract Object getPrimaryKey();            // Item / Fluid — for grouping in maps

    // --- serialisation (implemented by the subclass) ---
    public abstract void toTag(NBTTagCompound tag);
    public abstract void writeToPacket(ByteBuf data) throws IOException;

    // --- serialisation including the type tag (final, implemented in the base) ---
    public final void toTagGeneric(NBTTagCompound tag);
    public static AEKey fromTagGeneric(NBTTagCompound tag);          // null if the type is unknown
    public static void writeOptionalKey(ByteBuf buf, @Nullable AEKey key) throws IOException;
    public static AEKey readOptionalKey(ByteBuf buf) throws IOException;
    public static void writeKey(ByteBuf buf, AEKey key) throws IOException;
    public static AEKey readKey(ByteBuf buf) throws IOException;

    // --- delegates to AEKeyType (final) ---
    public final int getAmountPerUnit();
    public final int getAmountPerOperation();
    public final int getAmountPerByte();
    public final String getUnitSymbol();
    public final boolean supportsFuzzyRangeSearch();

    // --- fuzzy ---
    public int getFuzzySearchValue();                  // default 0
    public int getFuzzySearchMaxValue();               // default 0
    public final boolean fuzzyEquals(AEKey other, FuzzyMode mode);

    // --- display ---
    public final ITextComponent getDisplayName();      // caches computeDisplayName()
    protected abstract ITextComponent computeDisplayName();
    public String formatAmount(long amount, AmountFormat format);
    public String getModId();
    public ItemStack wrapForDisplayOrFilter();         // placeholder item for slots / HEI

    // --- misc ---
    public abstract AEKey dropSecondary();             // key without NBT / secondary data
    public final boolean matches(@Nullable GenericStack stack);
    public abstract void addDrops(long amount, List<ItemStack> drops, World world, BlockPos pos);
}
```

> **The role of `wrapForDisplayOrFilter()`**: in 1.12.2, GUI slots and HEI can only display an `ItemStack`. This method generalises what `appeng\fluids\items\FluidDummyItem` does today. For `AEItemKey` it returns the stack itself; for every other type, a wrapper item. It is the foundation of the multi-type GUI (wave 4).

### 1.2 `AEKeyType`

`src\api\java\appeng\api\stacks\AEKeyType.java`

The analogue of `IStorageChannel`. A **Forge registry** entry.

```java
public abstract class AEKeyType extends IForgeRegistryEntry.Impl<AEKeyType> {
    protected AEKeyType(ResourceLocation id, Class<? extends AEKey> keyClass, ITextComponent description);

    // --- built-in types ---
    public static AEKeyType items();
    public static AEKeyType fluids();
    public static AEKeyType fromRawId(int id);         // null if absent

    // --- identity ---
    public final ResourceLocation getId();
    public final Class<? extends AEKey> getKeyClass();
    public final int getRawId();                       // numeric id from the Forge registry, for packets
    public ITextComponent getDescription();

    // --- (de)serialisation of keys of this type ---
    public abstract AEKey readFromPacket(ByteBuf input) throws IOException;
    public abstract AEKey loadKeyFromTag(NBTTagCompound tag);   // null if unreadable

    // --- units ---
    public int getAmountPerOperation();                // default 1
    public int getAmountPerByte();                     // default 8
    public int getAmountPerUnit();                     // default 1
    public String getUnitSymbol();                     // default ""
    public final String formatAmount(long amount, AmountFormat format);

    // --- checks ---
    public final AEKey tryCast(AEKey key);             // null if it belongs to another type
    public final boolean contains(AEKey key);
    public final AEKeyFilter filter();
    public boolean supportsFuzzyRangeSearch();         // default false

    // --- GUI (absent in AE2-original; taken from ae-gtnh IAEStackType) ---
    public abstract ResourceLocation getButtonTexture();
    public abstract ItemStack getButtonIcon();
}
```

> **`getButtonTexture`/`getButtonIcon` are a deliberate addition.** In AE2-original the channel-switcher icons are not part of the type contract, because its GUI stack is different. ae-gtnh does have them (`IAEStackType.getButtonTexture()`/`getButtonIcon()`), and they are exactly what a multi-type GUI needs in order to avoid hardcoding Item/Fluid. This is the one place where we knowingly diverge from AE2-original.

**Numeric id.** `getRawId()` is the Forge registry id. Forge assigns them, synchronises them on login and remaps them on world load. We do **not** write manual numbering like ae-gtnh's `AEStackTypeRegistry.initNetworkIds()`.

### 1.3 `AEKeyTypes`

`src\api\java\appeng\api\stacks\AEKeyTypes.java`

The registry facade for addons.

```java
public final class AEKeyTypes {
    public static final ResourceLocation REGISTRY_NAME = new ResourceLocation("appliedenergistics2", "keytypes");
    public static final ResourceLocation ITEMS_ID  = new ResourceLocation("appliedenergistics2", "item");
    public static final ResourceLocation FLUIDS_ID = new ResourceLocation("appliedenergistics2", "fluid");

    public static IForgeRegistry<AEKeyType> getRegistry();
    public static void register(AEKeyType keyType);
    public static AEKeyType get(ResourceLocation id);   // null if absent
    public static Collection<AEKeyType> getAll();
    public static int getRawId(AEKeyType type);
    public static AEKeyType fromRawId(int id);
    public static AEKeyType items();
    public static AEKeyType fluids();
}
```

The registry is created in `RegistryEvent.NewRegistry` through `RegistryBuilder<AEKeyType>`, **before item registration**. An addon registers its own type with an ordinary `@SubscribeEvent RegistryEvent.Register<AEKeyType>`.

### 1.4 `AEItemKey` / `AEFluidKey`

`src\api\java\appeng\api\stacks\AEItemKey.java`, `AEFluidKey.java`

```java
public final class AEItemKey extends AEKey {
    public static AEItemKey of(ItemStack stack);        // null if the stack is empty
    public static AEItemKey of(Item item);
    public static AEItemKey of(Item item, int damage);
    public static boolean matches(AEKey what, ItemStack stack);
    public static boolean is(AEKey what);
    public static AEKeyFilter filter();

    public ItemStack toStack();                         // count = 1
    public ItemStack toStack(int count);
    public ItemStack getReadOnlyStack();                // do NOT mutate
    public Item getItem();
    public int getDamage();
    public NBTTagCompound getTag();                     // null if absent
    public boolean matches(ItemStack stack);
    public int getMaxStackSize();
    public boolean isDamaged();
    public static AEItemKey fromTag(NBTTagCompound tag);
    public static AEItemKey fromPacket(ByteBuf data) throws IOException;
}

public final class AEFluidKey extends AEKey {
    public static final int AMOUNT_BUCKET = 1000;

    public static AEFluidKey of(Fluid fluid);
    public static AEFluidKey of(FluidStack stack);      // null if the stack is null
    public static boolean matches(AEKey what, FluidStack fluid);
    public static boolean is(AEKey what);
    public static AEKeyFilter filter();

    public FluidStack toStack(int amount);
    public Fluid getFluid();
    public NBTTagCompound getTag();
    public boolean matches(FluidStack stack);
    public static AEFluidKey fromTag(NBTTagCompound tag);
    public static AEFluidKey fromPacket(ByteBuf data) throws IOException;
}
```

### 1.5 `GenericStack`

`src\api\java\appeng\api\stacks\GenericStack.java`

A key/amount pair. This is the type that takes over the role `IAEItemStack` plays today — but **immutable**.

```java
@Desugar
public record GenericStack(AEKey what, long amount) {
    public static final String AMOUNT_FIELD = "#";

    public static GenericStack fromItemStack(ItemStack stack);      // null if empty
    public static GenericStack fromFluidStack(FluidStack stack);
    public static long getStackSizeOrZero(@Nullable GenericStack stack);
    public static GenericStack sum(GenericStack a, GenericStack b); // null if the keys differ

    public static GenericStack readTag(NBTTagCompound tag);
    public static void writeTag(NBTTagCompound tag, @Nullable GenericStack stack);
    public static GenericStack readBuffer(ByteBuf buf) throws IOException;
    public static void writeBuffer(@Nullable GenericStack stack, ByteBuf buf) throws IOException;

    // wrapping into an ItemStack for slots / GUIs (the FluidDummyItem role)
    public static ItemStack wrapInItemStack(@Nullable GenericStack stack);
    public static ItemStack wrapInItemStack(AEKey what, long amount);
    public static boolean isWrapped(ItemStack stack);
    public static GenericStack unwrapItemStack(ItemStack stack);    // null if not a wrapper

    // SPI installed by the mod during init; addons never implement this
    public interface Wrapper {
        ItemStack wrap(AEKey what, long amount);
        boolean isWrapped(ItemStack stack);
        GenericStack unwrap(ItemStack stack);
    }
    public static void setWrapper(Wrapper wrapper);
}
```

### 1.6 `KeyCounter`

`src\api\java\appeng\api\stacks\KeyCounter.java`

The multi-type "key → amount" collection. Replaces `IItemList<T>`. Backed by fastutil's `Object2LongMap<AEKey>` (available, already used in 20 files under `src/main`), grouped by `AEKey.getPrimaryKey()` for fast fuzzy lookups.

```java
public final class KeyCounter implements Iterable<Object2LongMap.Entry<AEKey>> {
    public void add(AEKey key, long amount);
    public void remove(AEKey key, long amount);
    public long remove(AEKey key);
    public void set(AEKey key, long amount);
    public long get(AEKey key);
    public void addAll(KeyCounter other);
    public void removeAll(KeyCounter other);
    public void removeZeros();
    public void removeEmptySubmaps();
    public void reset();                                // zeroes values, keeps keys
    public void clear();
    public boolean isEmpty();
    public int size();
    public Set<AEKey> keySet();
    public Collection<Object2LongMap.Entry<AEKey>> findFuzzy(AEKey key, FuzzyMode fuzzy);
    public AEKey getFirstKey();
    public <T extends AEKey> T getFirstKey(Class<T> keyClass);
    public Object2LongMap.Entry<AEKey> getFirstEntry();
    public Iterator<Object2LongMap.Entry<AEKey>> iterator();
}
```

> **The difference from `IItemList` that is easy to miss:** `IItemList` stored mutable `IAEStack` elements and let callers change an amount via `setStackSize` on the element. Here the value is a `long` in a map; the amount can **only** be changed through `add`/`set`/`remove` on the `KeyCounter` itself. Code that used to mutate a stack in place must be rewritten, not adapted.

`add` and the methods built on it use saturated arithmetic: positive overflow clamps to `Long.MAX_VALUE`
and negative underflow clamps to `Long.MIN_VALUE`. Network aggregation must never wrap a very large stored
amount into the opposite sign.

### 1.7 `AmountFormat`

`src\api\java\appeng\api\stacks\AmountFormat.java`

```java
public enum AmountFormat {
    FULL,            // full number with group separators
    PREVIEW_REGULAR, // abbreviated (1.2K) — for terminals
    PREVIEW_LARGE,   // abbreviated, more significant digits
    SLOT             // shortest possible — drawn on a slot icon
}
```

---

## 2. `appeng.api.storage`

### 2.1 `MEStorage`

`src\api\java\appeng\api\storage\MEStorage.java`

Replaces `IMEInventory<T>`. **Not generic** — one object serves keys of any type.

```java
public interface MEStorage {
    default boolean isPreferredStorageFor(AEKey what, IActionSource source) { return false; }
    default long insert(AEKey what, long amount, Actionable mode, IActionSource source) { return 0; }
    default long extract(AEKey what, long amount, Actionable mode, IActionSource source) { return 0; }
    default void getAvailableStacks(KeyCounter out) {}
    default KeyCounter getAvailableStacks() { /* creates and fills */ }
    default ITextComponent getDescription() { /* name for the UI */ }
}
```

### 2.2 `AEKeyFilter`

`src\api\java\appeng\api\storage\AEKeyFilter.java`

```java
@FunctionalInterface
public interface AEKeyFilter {
    boolean matches(AEKey what);
    static AEKeyFilter none();
    static AEKeyFilter all();
    default AEKeyFilter and(AEKeyFilter other);
    default AEKeyFilter or(AEKeyFilter other);
}
```

---

## 3. `appeng.api.behaviors` — the strategy layer

A new package. This is the other half of extensibility: the type registry says "gas exists", the strategy layer says "here is how to get gas out of the adjacent tank". Without it a registered type connects to nothing.

All five strategies share one shape: an interface, a nested `Factory`, and a static `register`.

```java
public interface ExternalStorageStrategy {
    MEStorage createWrapper(boolean extractableOnly, Runnable injectOrExtractCallback);
    @FunctionalInterface interface Factory {
        ExternalStorageStrategy create(World world, BlockPos fromPos, EnumFacing fromSide);
    }
    static void register(AEKeyType type, Factory factory);
}

public interface StackImportStrategy {
    boolean transfer(StackTransferContext context);
    @FunctionalInterface interface Factory {
        StackImportStrategy create(World world, BlockPos fromPos, EnumFacing fromSide);
    }
    static void register(AEKeyType type, Factory factory);
}

public interface StackExportStrategy {
    long transfer(StackTransferContext context, AEKey what, long maxAmount);
    long push(AEKey what, long maxAmount, Actionable mode);
    @FunctionalInterface interface Factory {
        StackExportStrategy create(World world, BlockPos fromPos, EnumFacing fromSide);
    }
    static void register(AEKeyType type, Factory factory);
}

public interface PlacementStrategy {
    static PlacementStrategy noop();
    void clearBlocked();
    long placeInWorld(AEKey what, long amount, Actionable type, boolean placeAsEntity);
    interface Factory {
        PlacementStrategy create(World world, BlockPos fromPos, EnumFacing fromSide,
                                 TileEntity host, @Nullable UUID owningPlayerId);
    }
    static void register(AEKeyType type, Factory factory);
}

public interface PickupStrategy {
    void reset();
    boolean canPickUpEntity(Entity entity);
    boolean pickUpEntity(IEnergySource energySource, PickupSink sink, Entity entity);
    Result tryPickup(IEnergySource energySource, PickupSink sink);
    enum Result { CANT_PICKUP, PICKED_UP, CANT_STORE }
    interface Factory {
        PickupStrategy create(World world, BlockPos fromPos, EnumFacing fromSide,
                              TileEntity host, Map<Enchantment, Integer> enchantments,
                              @Nullable UUID owningPlayerId);
    }
    static void register(AEKeyType type, Factory factory);
}

@FunctionalInterface
public interface PickupSink {
    long insert(AEKey what, long amount, Actionable mode);
}

public interface StackTransferContext {
    MEStorage getInternalStorage();
    int getOperationsRemaining();
    void setOperationsRemaining(int operationsRemaining);
    boolean hasOperationsLeft();
    boolean hasRegularOperationsLeft();
    void reduceOperationsRemaining(long amount);
    IActionSource getActionSource();
    AEKeyFilter getFilter();
}
```

> **Deviation from upstream in `PickupStrategy.Factory`:** AE2-original passes `ItemEnchantments`, a modern data type with no 1.12.2 equivalent, and its own plane reads only a fortune level and a silk-touch flag. AE2UD's energy-cost formula also reads Efficiency and Unbreaking, so the factory carries the whole `Map<Enchantment, Integer>` the plane captured when it was placed. A strategy for a type that has no enchantment concept (fluids) simply ignores it. See §8.4 for why this was amended after wave 3.

### 3.1 `StackWorldBehaviors`

`src\api\java\appeng\api\behaviors\StackWorldBehaviors.java`

> **Changed during wave 0 implementation.** The registry was originally meant to live in `src/main` (as in AE2-original, which has no separate api module). That is impossible: `src/api` is a separate source set and **does not depend on `main`**, so the static `register(...)` methods on the strategies would have nothing to point at. The registry moved into api; it has no dependencies outside api.

```java
public final class StackWorldBehaviors {
    public static void registerImportStrategy(AEKeyType type, StackImportStrategy.Factory f);
    public static void registerExportStrategy(AEKeyType type, StackExportStrategy.Factory f);
    public static void registerExternalStorageStrategy(AEKeyType type, ExternalStorageStrategy.Factory f);
    public static void registerPlacementStrategy(AEKeyType type, PlacementStrategy.Factory f);
    public static void registerPickupStrategy(AEKeyType type, PickupStrategy.Factory f);

    public static AEKeyFilter hasImportStrategyFilter();
    public static Predicate<AEKeyType> hasImportStrategyTypeFilter();
    public static AEKeyFilter hasExportStrategyFilter();
    public static AEKeyFilter hasPlacementStrategy();
    public static Set<AEKeyType> withImportStrategy();
    public static Set<AEKeyType> withExportStrategy();
    public static Set<AEKeyType> withPlacementStrategy();

    public static List<StackImportStrategy> createImportStrategies(World w, BlockPos pos, EnumFacing side,
            Predicate<AEKeyType> forTypes);
    public static List<StackExportStrategy> createExportStrategies(World w, BlockPos pos, EnumFacing side);
    public static Map<AEKeyType, ExternalStorageStrategy> createExternalStorageStrategies(
            World w, BlockPos pos, EnumFacing side);
    public static Map<AEKeyType, PlacementStrategy> createPlacementStrategies(World w, BlockPos pos,
            EnumFacing side, TileEntity host, @Nullable UUID owningPlayerId);
    public static List<PickupStrategy> createPickupStrategies(World w, BlockPos pos, EnumFacing side,
            TileEntity host, Map<Enchantment, Integer> enchantments, @Nullable UUID owningPlayerId);
}
```

The built-in item and fluid implementations register through **the same public API** an addon would use. Item and Fluid get no privileges.

---

## 4. The rest of `src/api`: what is deleted and what is renamed

**This is not a tail end, it is a mandatory part of wave 0.** **37 files inside `src/api` itself** depend on the types in §4.1, so `compileApiJava` cannot pass until all of them are brought to the new shape. Deferring them to wave 1 is impossible — the wave 0 gate would simply never work.

### 4.1 Deleted outright

```
storage\data\IAEStack.java          storage\IStorageChannel.java
storage\data\IAEItemStack.java      storage\channels\IItemStorageChannel.java
storage\data\IAEFluidStack.java     storage\channels\IFluidStorageChannel.java
storage\data\IItemList.java         storage\IMEInventory.java
storage\data\IItemContainer.java    storage\IMEInventoryHandler.java
storage\IMEMonitor.java             storage\IStorageMonitorable.java
```

`IMEInventory`, `IMEInventoryHandler`, `IMEMonitor` and `IStorageMonitorable` **are not renamed, they disappear** — `MEStorage` takes over their role entirely. This is the most common trap: 41 files mention `IMEMonitor` and the temptation to "just rename" is strong. Do not rename — replace with `MEStorage`, and move change subscription (the reason `IMEMonitor` existed) to `IStorageWatcherNode`/`IStackWatcher`.

`IStorageHelper` loses `registerStorageChannel`/`getStorageChannel`/`storageChannels()` — `AEKeyTypes` (§1.3) takes over.

### 4.2 Renamed (full alignment with AE2-original)

| AE2UD before                               | Becomes                                 | Package                     |
| ------------------------------------------ | --------------------------------------- | --------------------------- |
| `IStorageGrid`                             | `IStorageService`                       | `api.networking.storage`    |
| `IStackWatcherHost`                        | `IStorageWatcherNode`                   | `api.networking.storage`    |
| `IStackWatcher`                            | `IStackWatcher` (name unchanged)        | `api.networking.storage`    |
| `IBaseMonitor`                             | — (deleted, absorbed by the watchers)   | —                           |
| `ICellProvider` + `ICellContainer`         | `IStorageProvider` + `IStorageMounts`   | `api.storage`               |
| `ICellRegistry`                            | `StorageCells`                          | `api.storage`               |
| `ICellHandler`                             | `ICellHandler`                          | `api.storage.cells`         |
| `ICellInventory` + `ICellInventoryHandler` | `StorageCell`                           | `api.storage.cells`         |
| `ICellWorkbenchItem`                       | `ICellWorkbenchItem`                    | `api.storage.cells`         |
| `IStorageCell`                             | `IBasicCellItem`                        | `api.storage.cells`         |
| `ISaveProvider`                            | `ISaveProvider`                         | `api.storage.cells`         |
| `IMEMonitorHandlerReceiver`                | — (deleted)                             | —                           |

The ones marked "name unchanged" still move into the new package along with the rest, so the package layout matches AE2-original and code written against upstream does not have to fix imports by hand.

### 4.3 Signatures

```java
// api.networking.storage
public interface IStorageService extends IGridCache {
    MEStorage getInventory();
    KeyCounter getCachedInventory();
    void addGlobalStorageProvider(IStorageProvider provider);
    void removeGlobalStorageProvider(IStorageProvider provider);
    void refreshNodeStorageProvider(IGridNode node);
    void refreshGlobalStorageProvider(IStorageProvider provider);
    void invalidateCache();
}

public interface IStorageWatcherNode {
    void updateWatcher(IStackWatcher newWatcher);
    void onStackChange(AEKey what, long amount);
}

public interface IStackWatcher {
    void setWatchAll(boolean watchAll);
    void add(AEKey key);
    void remove(AEKey key);
    void reset();
}

// api.storage
public interface IStorageProvider {
    void mountInventories(IStorageMounts mounts);
    static void requestUpdate(IGridNode node);
}

public interface IStorageMounts {
    int DEFAULT_PRIORITY = 0;
    default void mount(MEStorage inventory) { mount(inventory, DEFAULT_PRIORITY); }
    void mount(MEStorage inventory, int priority);
}

public final class StorageCells {
    public static synchronized void addCellHandler(ICellHandler handler);
    public static synchronized boolean isCellHandled(ItemStack is);
    public static synchronized ICellHandler getHandler(ItemStack is);   // null if none
    public static synchronized StorageCell getCellInventory(ItemStack is, @Nullable ISaveProvider host);

    // AE2UD-specific: upstream has no GUI-handler concept. See §9 note.
    public static synchronized void addCellGuiHandler(ICellGuiHandler handler);
    public static synchronized ICellGuiHandler getGuiHandler(AEKeyType keyType);
    public static synchronized ICellGuiHandler getGuiHandler(AEKeyType keyType, ItemStack cell);
}

// api.storage.cells
public interface ICellHandler {
    boolean isCell(ItemStack is);
    StorageCell getCellInventory(ItemStack is, @Nullable ISaveProvider host);   // null if not a cell
}

public interface StorageCell extends MEStorage {
    CellState getStatus();
    double getIdleDrain();
    default boolean canFitInsideCell() { return true; }
    void persist();
}

public enum CellState {
    ABSENT(0), EMPTY(0x00FF00), NOT_EMPTY(0x00AAFF), TYPES_FULL(0xFFAA00), FULL(0xFF0000);
    public int getStateColor();
}

public interface IBasicCellItem extends ICellWorkbenchItem {
    AEKeyType getKeyType();
    int getBytes(ItemStack cellItem);
    int getBytesPerType(ItemStack cellItem);
    int getTotalTypes(ItemStack cellItem);
    double getIdleDrain();
    default boolean isBlackListed(ItemStack cellItem, AEKey requestedAddition) { return false; }
    default boolean storableInStorageCell() { return false; }
    default boolean isStorageCell(ItemStack i) { return true; }
}

public interface ISaveProvider {
    void saveChanges();
}
```

> **`IBasicCellItem.getKeyType()`** is where a cell declares its type. It is what makes "a cell for gas" possible without touching the core: an addon registers an `AEKeyType`, ships a cell item returning that type, and drives, chests, the workbench and the terminal all keep working unchanged.

### 4.4 Crafting API — names stay, signatures change

`ICraftingCPU`, `ICraftingGrid`, `ICraftingJob`, `ICraftingPatternDetails`, `ICraftingProviderHelper`, `ICraftingRequester`, `ICraftingWatcher`, `ICraftingWatcherHost` (8 files) **keep their names**. Only the typing changes: `IAEItemStack` → `AEKey` where identity is meant, → `GenericStack` where "what plus how much" is meant; `IItemList<IAEItemStack>` → `KeyCounter`.

**Why crafting is not aligned too:** in AE2-original autocrafting is a separate large redesign (`IPatternDetails`, `ICraftingPlan`, `CalculationStrategy`, `ICraftingSubmitResult`), not a rename. Dragging it in here would merge two independent projects into one campaign that is already running without a compiler. Aligning the crafting API is a candidate for a separate phase after v1.

The same rule applies to `MENetworkStorageEvent`, `IPortableCell`, `IPartStorageMonitor`, `IClientHelper` and `ICellGuiHandler` — names stay, types change by the same mapping.

## 5. Out of scope for wave 0

Deliberately **not** ported:

- `AEKey.isTagged(TagKey)` and `AEKey.get(DataComponentType)` — 1.12.2 has neither tags nor data components. An ore-dictionary analogue can be added later as a separate method if needed.
- All `Codec`/`MapCodec`/`StreamCodec` — replaced by explicit NBT/ByteBuf methods.
- Reading the old NBT format — save compatibility is deliberately broken.
- The multi-type filter GUI — to be designed before wave 4; no 1.12.2 precedent exists in any source.

## 6. The gate

```
gradlew compileApiJava
```

Must pass **before** wave 1 starts. It is the only automatic check in the whole campaign: the project has no tests covering storage (`src/test` is 11 files about a version parser, `UUIDMatcher` and similar), so the rest of verification is manual, in game.

## 7. Waves

> **Corrected 2026-07-27.** The original table was drawn up from the names of the main packages and covered only **41%** of the tree: 90 broken files belonged to no wave at all (`core/*`, `fluids/*`, `tile/*`, `client/me`, `client/render`, `helpers`, `capabilities`, `recipes`, `container/slot`). Below is the recounted version.

| Wave | Scope                                                                                                          | Files | End of wave                        |
| ---- | -------------------------------------------------------------------------------------------------------------- | ----: | ---------------------------------- |
| 0    | all of `src/api` (§1–§4)                                                                                        |    31 | `compileApiJava` + owner review    |
| 1    | `appeng.util`, `appeng.me`                                                                                      |    54 | §9 filled in ✔                     |
| 2    | `appeng.crafting`, `appeng.tile`, `appeng.helpers`, `appeng.capabilities`, `appeng.core` (except `core.sync`)    |   ~39 | §9 extended                        |
| 3    | `appeng.parts`, `appeng.items`, `appeng.recipes`                                                                |   ~25 | §9 extended                        |
| 4    | `appeng.core.sync`, `appeng.container`, `appeng.client` — including the **multi-type filter GUI**                |   ~49 | §9 extended                        |
| 5    | `appeng.fluids` — the whole package                                                                             |    35 | §9 extended                        |
| 6    | `appeng.integration` (plus the switch to HEI, §8.2)                                                             |     7 | **green build** ✔ + manual in-game test |

**All seven waves are done and `gradlew build` is green.** The file counts above were the plan; the actual
counts are in `STATUS.md`'s commit table. Wave 6 came in at 7 files rather than ~34 because the earlier waves
had already covered the rest of `appeng.integration` — but its 7 files were the smallest part of it, exactly
as predicted: the first real compile turned up 26 errors spread across waves 1–5. The one thing still
outstanding from this table is the **manual in-game test**.

The order is not arbitrary. `crafting`/`tile`/`helpers` (wave 2) are what the parts depend on. `fluids` is deliberately pushed to the **end**: by wave 5 both the generic-part pattern and the multi-type GUI pattern will exist for it to mirror, and under the v1 plan that package is only mechanically translated anyway (decomposing `appeng.fluids.*` into the generic model is a post-v1 phase).

Waves 2–5: 3–5 agents in parallel on disjoint packages. After each wave this file is updated with the classes created and their public signatures, so the next wave works against real names rather than invented ones.

## 8. What changed during wave 0 implementation

The specification was written before the code; this is what the code corrected. **These points are already reflected in §1–§4 above** — they are listed separately so the change can be reviewed rather than hunted for in a diff.

**Build**

1. **`src/api` was compiling with `-source 8`, without Jabel.** `tasks.withType(JavaCompile)` sets `sourceCompatibility 17` on every task, but the Jabel processor was only wired to `annotationProcessor`/`testAnnotationProcessor`. Added `apiAnnotationProcessor`/`apiCompileOnly` in `build.gradle`. Records and pattern matching now work in api too; the bytecode is still 8, so addons on Java 8 are unaffected. `GenericStack` needs `@Desugar` (like `Tooltips.MaxedAmount` in `main`).
2. **`StackWorldBehaviors` moved into `src/api`** (`appeng.api.behaviors`). `src/api` does not depend on `main`, so the static `register(...)` methods on the strategies would have had nothing to point at.

**API**

3. **`GenericStack.Wrapper` is a new SPI.** The wrapper item for non-item keys must be a registered item, so it lives in `main`. The mod installs the implementation via `GenericStack.setWrapper(...)` during init; addons never touch it.
4. **`AEKeyFormatting` is a new package-private class.** `formatAmount` cannot call the mod's own number formatters, for the same source-set isolation reason.
5. **`AEKey.supportsFuzzyRangeSearch()` stayed `final`.** `AEItemKey` does not override it: item damageability is expressed through `getFuzzySearchMaxValue()`, and `fuzzyEquals` treats `maxValue <= 0` as exact comparison. Same as AE2-original.
6. **`createImportFacade`/`createExportFacade` became `createImportStrategies`/`createExportStrategies`** returning `List<...>`, and `createPlacementStrategies` returns `Map<AEKeyType, PlacementStrategy>`. The facade classes (`StackImportFacade` and friends) are implementation and belong in `main`; api hands out the parts and `main` composes them. Wave 3 must create those facades.
7. **`ITerminalHost` gained `MEStorage getInventory()`.** It used to extend `IStorageMonitorable`, which is deleted; without this the interface would be empty.
8. **`MENetworkStorageEvent` lost its `channel` field** — only `public final MEStorage storage` remains. There are no channels any more.
9. **`IRegistryContainer.cell()` removed** — `StorageCells` is static now.
10. **`IStorageHelper.poweredExtraction`/`poweredInsert` now return `long`** and take `(MEStorage inv, AEKey what, long amount, ...)` instead of a generic `T`. "Return a stack" makes no sense once the amount is separated from the key.
11. **`ICraftingCPU` no longer extends `IBaseMonitor`** (deleted along with the rest of the monitor layer).
12. **`AEKeyType.getRawId()` returns `int`**, not `byte` — Forge hands out int ids and there is no reason to narrow them.
13. **`AEKeyTypes` has `ITEMS_ID`/`FLUIDS_ID` constants** — the built-in types are looked up in the registry by them, because api cannot reference their implementations in `main`.

**Status:** `gradlew compileApiJava` — **BUILD SUCCESSFUL**. `src/main` is broken as expected, which is the normal state until the end of wave 6.

## 8.1 Decisions from the wave 0 review

Three contentious points were reviewed by the owner and approved as they stand:

1. **`getButtonTexture()`/`getButtonIcon()` stay `abstract`.** An addon is required to supply them. The alternative (default stubs) would turn "forgot the icon" from a compile error into a runtime bug in front of a player.
2. **`wrapForDisplayOrFilter()` stays.** There is no alternative in 1.12.2 — slots and HEI only understand `ItemStack`. **Requirement this places on wave 3:** the wrapper item (`GenericStack.Wrapper`) must not stack, must have no recipe, must not appear in the creative tab, and must be cleaned out of a player's inventory on tick. Otherwise a player ends up holding an item that pretends to be gas.
3. **The crafting API keeps its own names** (§4.4). Aligning with `IPatternDetails`/`ICraftingPlan` is a separate phase after v1, because it is a different autocrafting architecture, not a rename.

## 8.2 HEI instead of JEI

The target environment uses **HadEnoughItems** (CleanroomMC, `51d34dba` @ 2026-07-10, version 4.32.0) — a 1.12.2 fork of JEI, not upstream JEI.

This is almost transparent for the port: HEI's `gradle.properties` sets `mod_id = jei` and `root_package = mezz`, and the API lives in the same `mezz.jei.api` in a separate source set, so integration code is source-compatible.

Consequences:

- **`build.gradle:583`** currently pulls `mezz.jei:jei_1.12.2:4.16.1.302`. Switch it to HEI 4.32.0 from the CleanroomMC maven — **a wave 6 task** (it does not block waves 1–5, the API is identical).
- **`appeng.integration.modules.jei`** — 18 files, 2 of which touch deleted types (`CraftableCallBack.java:31` — `IItemList<IAEItemStack> list` → `KeyCounter`). Wave 6 scope.
- **A hook for the multi-type GUI (wave 4):** `mezz.jei.api.ingredients.IModIngredientRegistration.register(IIngredientType<V>, Collection<V>, IIngredientHelper<V>, IIngredientRenderer<V>)` plus `markAsCraftable(IIngredientType<V>)`. This allows registering **each `AEKeyType` as its own HEI ingredient type**, so ghost-drag into a filter slot works for any type without hardcoding Item/Fluid. `VanillaTypes.ITEM`/`FLUID` show the shape: `IIngredientType<V>` is just `() -> V.class`.

## 8.3 Amendment after wave 1a — `ICraftingGrid.getCraftables()`

**This was a change to the frozen API. Approved by the owner 2026-07-27.**

A wave 1a agent, per rule 2, stopped instead of inventing a workaround and reported: the old `IAEItemStack` carried a **craftable** flag (plus `countRequestable`), and `KeyCounter` only has a `long`. After migration a terminal could no longer tell that the network has a pattern for an item it does not physically stock — the "craftable" rows in the terminal would simply vanish.

Checked against AE2-original: the flag is not lost, it **moved off the stack onto the crafting service** — `ICraftingService.getCraftables(AEKeyFilter)` plus a `default isCraftable(AEKey)`. Our `ICraftingGrid` had neither, because the contract kept crafting under its old names (§4.4) and I did not compare it method by method against the new model.

Added to `src\api\java\appeng\api\networking\crafting\ICraftingGrid.java`:

```java
Set<AEKey> getCraftables( AEKeyFilter filter );
default boolean isCraftable( AEKey what );
```

The change is **additive** (breaks nothing), mirrors AE2-original verbatim, and the model does not work without it. `compileApiJava` is green. But §7 says post-freeze edits to §1–§4 are the owner's call, so this stays open for review.

**Related requirement for the waves that follow:** `appeng.crafting.MECraftingInventory`, `appeng.me.storage.MEMonitorPassThrough`, `PartStorageBus` and `PartFluidStorageBus` relied on the stored/craftable distinction living in the stack itself. They must query `ICraftingGrid` instead of looking for a flag. `appeng.util.inv.ItemListIgnoreCrafting` was deleted rather than ported for the same reason — there is nothing left to strip.

**Requirement for wave 2 (from gap #1):** `Platform.postChanges` is currently implemented as `gs.invalidateCache()` — correct, but not incremental. The `IStorageService` implementation keeps `cachedAvailableAmounts` and diffs it against a fresh `KeyCounter` on refresh, calling `IStorageWatcherNode.onStackChange(what, newAmount)` for changed and removed keys — as in `AE2-original\src\main\java\appeng\me\service\StorageService.java:120-156`. Nothing needs to be added to the API for this.

## 8.4 Amendment after wave 3a — `PickupStrategy.Factory` carries the enchantment map

**This was a change to the frozen API. Approved by the owner 2026-07-28.**

The frozen factory took `int fortuneLevel, boolean silkTouch`, mirroring AE2-original, and §3 asserted those were the only two enchantments the annihilation plane reads. That assertion was wrong about this fork: AE2UD's energy-cost formula also reads **Efficiency** (reduces the surcharge 15% per level) and **Unbreaking** (randomised discount). Both were in the pre-port `PartAnnihilationPlane`, lines 486-494.

The wave 3a agent kept the mechanic without touching api, by making `PartAnnihilationPlane` build its `ItemPickupStrategy` directly from its own enchantment map and filtering the registered one back out. Behaviour was preserved, but the strategy handed out by `StackWorldBehaviors.createPickupStrategies` stayed lossy — so an addon registering a key type and relying on the registry would have got the wrong energy model. That defeats the reason the strategy layer exists (§3, "Item and Fluid get no privileges").

Changed in `src\api\java\appeng\api\behaviors\PickupStrategy.java` and `StackWorldBehaviors.java`:

```java
PickupStrategy create(World world, BlockPos fromPos, EnumFacing fromSide, TileEntity host,
                      Map<Enchantment, Integer> enchantments, @Nullable UUID owningPlayerId);
```

This is a **deliberate divergence from upstream**, of exactly the kind rule 6 requires: the fork has a mechanic upstream does not, and the upstream-shaped signature could not carry it. A strategy for a type with no enchantment concept ignores the parameter. `PartAnnihilationPlane.createPickupStrategies` now simply delegates to the registry, and `PartIdentityAnnihilationPlane` still overrides the hook to substitute its always-silk-touch strategy. `compileApiJava` is green.

## 8.5 Amendment after the first play-test — `wrapForDisplayOrFilter()` wraps with amount 0

**This was a change to the frozen API. Made 2026-07-29, approved by the owner 2026-08-01.**

`AEKey.wrapForDisplayOrFilter()` wrapped with amount **1**. That stack stands for an *identity* — it is what
a terminal row, a filter slot or HEI is handed when only an `ItemStack` will do — and it never stands for a
quantity, because the real amount is drawn beside the slot and belongs to the row.

The placeholder amount leaked out as a wrong number: a terminal row for water read **`Water: 0B`**, because
`WrappedGenericStack`'s tooltip formatted the wrapper's own amount and one millibucket rounds to zero
buckets. It wraps with `0` now, and `WrappedGenericStack.addCheckedInformation` prints an amount line only
when the wrapper genuinely carries one — which distinguishes a display placeholder from a configured filter
entry, something amount `1` made inexpressible.

Audited before changing: the only two callers of `GenericStack.unwrapItemStack` (`ApiClientHelper` and
`ItemViewCell`) read `what()` and never the amount.

## 8.6 Amendment after the follow-up sweep — no-argument `ICraftingGrid.getCraftables()`

**This was a change to the frozen API. Approved by the owner 2026-07-31.**

`ContainerMEMonitorable` asks for the craftable set **every tick, for every open terminal**, and diffs it
against last tick's. §8.3 gave it only `getCraftables(AEKeyFilter)`, so every one of those calls allocated a
fresh `HashSet` over every pattern output plus every emitable key, and the diff that followed was O(N)
whether or not anything had changed. Patterns change when a player edits one; availability changes
constantly. The two were being paid for at the same rate.

Added:

```java
default Set<AEKey> getCraftables();
```

`default`, not abstract, so an addon's `ICraftingGrid` keeps compiling - it delegates to the filtered form.
The contract that makes it worth having is stated on the method: an implementation returns an **immutable
set and the same instance** for as long as patterns and emitters do not change, so a caller holding the
previous answer recognises "unchanged" by identity. A different instance means only that the answer *may*
have changed, which is why the delegating default is conservative rather than wrong.

`CraftingGridCache` overrides it with a lazily built `ImmutableSet`, nulled at the two places that mutate
`craftableItems`/`emitableItems`. `ContainerMEMonitorable` skips both diff sets when the reference is
unchanged, and its `previousCraftables` javadoc now records why whatever it stores has to be immutable -
the same aliasing hazard as `IStorageService.getCachedInventory()`.

**Rejected alternative:** an `AEKeyFilter.ALL` constant plus an identity test inside `getCraftables(filter)`.
Smaller api surface, but it would make the result's mutability depend on which filter was passed - shared
for `ALL`, fresh for anything else - and the caller stores that reference. That is the trap, not a saving.

**Found while doing it, and the larger half of the win:** `CraftingGridCache` never overrode
`default isCraftable(AEKey)`, so a single-key question walked every pattern and built a set for the answer.
Both collections are keyed by `AEKey`; it is a lookup. The hot caller is `DualityInterface`, which asks
whenever an extraction comes up short - per slot, per update, exactly when a network is starved. No api
change was needed for this one; the method was already `default` so that an implementation could do better.

## 8.7 Amendment — `AEKeyType.getDefaultCraftAmount()`

**This was a change to the frozen API. Approved by the owner 2026-07-31.**

Ordering a fluid craft offered `1` — one millibucket. The number was a literal in
`GuiCraftAmount.initGui`, so it could not know what it was counting.

Added:

```java
default long getDefaultCraftAmount();   // returns getAmountPerUnit()
```

Behaviour matches upstream exactly: `MEStorageMenu` and `ConversionMonitorPart` pass
`clickedKey.getAmountPerUnit()` when they open the screen. What differs is only that the policy has a
name here instead of being a call-site coincidence, which is what lets a type separate the two. The
motivating case is energy — upstream's own `NumberEntryType.ENERGY` has `amountPerUnit = 1`, yet nobody
orders energy in single AE. Neither `AEItemKeyType` nor `AEFluidKeyType` overrides it.

**Why the default is `getAmountPerUnit()` and not `1`.** With `1`, a type that declares a display unit
and forgets this method offers a thousandth of one — the very defect being fixed, silently, as the
default path for everything written later. A method you must override to avoid a bug is a trap, not an
extension point; overriding should buy something *different*, not something *working*.

**Why the screen carries the amount over `@GuiSync` and not in the display slot.** Upstream packs it
into the slot via `GenericStack.wrapInItemStack(what, initialAmount)` with `setHideAmount(true)`. That
cannot work here: for an `AEItemKey` our wrapper returns a real `ItemStack`, whose `Count` 1.12
serialises as a **byte**. A starting amount of 1000 items would not survive the trip.
`ContainerCraftAmount` gets `@GuiSync(10) long initialAmount`, filled at the single place that opens
`GUI_CRAFTING_AMOUNT` (`PacketInventoryAction`), which leaves room for a caller to offer something other
than the type's suggestion — upstream's "craft the missing amount" works exactly that way.

## 8.8 Amendment — `KeyTypeSelection` and `KeyTypeSelectionHost`

**This was an addition to the frozen API. Approved by the owner 2026-08-01.**

An import bus with an empty filter takes whatever the neighbouring block offers. That was harmless while
"whatever" meant items; once every registered type has an import strategy, a bus that only wants items has
no way to say so. Upstream solves it with `appeng.api.util.KeyTypeSelection` plus a `KeyTypeSelectionHost`
marker, and so do we, at the same package and with the same method names.

```java
// appeng.api.util.KeyTypeSelection
public KeyTypeSelection(Listener listener, Predicate<AEKeyType> allowKeyType);
public void setEnabled(AEKeyType type, boolean enabled);   // refuses to turn the last one off
public boolean isEnabled(AEKeyType type);
public Map<AEKeyType, Boolean> enabled();                  // registration order
public List<AEKeyType> enabledSet();
public Predicate<AEKeyType> enabledPredicate();
public void writeToNBT(NBTTagCompound tag);
public void readFromNBT(NBTTagCompound tag);

// appeng.api.util.KeyTypeSelectionHost
KeyTypeSelection getKeyTypeSelection();
```

`readFromNBT` differs from upstream in one deliberate way. Upstream, finding no enabled types, turns the
first one on. Ours distinguishes an **absent** tag from an **empty** one: absent means the machine was
saved before it had a selection, and it must keep acting on every type. Upstream never faces this — the
feature and the part were written together. Falling back to "first type only" would have quietly stopped
every existing bus in a world from importing fluids, which is rule 6.

**`ISubMenuHost` is `appeng.helpers`, not `appeng.api`.** Upstream's `KeyTypeSelectionHost` implementors
are also `ISubMenuHost`, whose job is telling a sub-screen where to send the player back to. Upstream
expresses that as `returnToMainMenu(Player, ISubMenu)` plus `getMainMenuIcon()`. In this version returning
is a `GuiBridge` switched by packet, and `GuiBridge` is in `src/main` — while `src/api` imports nothing
from `src/main` anywhere in this port, deliberately. So the interface lives in `appeng.helpers` with
`getGuiBridge()`/`getItemStackRepresentation()`, the two methods `IPriorityHost` already carried, and
`IPriorityHost` now extends it. The API half stays free of it: `KeyTypeSelectionHost` declares only
`getKeyTypeSelection()`.

## 8.9 Amendment — fluid substitution on `ICraftingPatternDetails`

**This was an addition to the frozen API. Approved by the owner 2026-08-01.**

Upstream's `AECraftingPattern` carries a `canSubstituteFluids` flag beside `canSubstitute`, and expresses
the consequence through `IInput.getPossibleInputs()` plus `IInput.getRemainingKey()`. Neither of those types
exists here - this version's pattern still speaks in `GenericStack[]` and `getSubstituteInputs(int)` - so the
same two facts are carried as two default methods, both additive:

```java
// appeng.api.networking.crafting.ICraftingPatternDetails
default boolean canSubstituteFluids();          // effective, not the raw flag
default boolean isContainerFabricated(int slot);
```

`canSubstituteFluids()` answers **false** for a pattern whose option is on but which has no ingredient that
qualifies. Every consumer - the tooltip line, the interface's refusal to hand the pattern to a third-party
`ICraftingMachine` - is then stating something true about what the pattern will do, rather than about what
the player ticked.

`isContainerFabricated(slot)` is decided by the pattern and not by what happens to sit in the slot, which is
only possible because such a slot is supplied *only* from the network as a key. That equivalence is the
whole design: a fabricated container and a real one are the same `ItemStack`, and
`TileMolecularAssembler` must still work it out after a chunk reload, when all it has is the pattern item
and the grid contents.

**The empty-container rule is not in `src/api`.** Deciding what a slot leaves behind needs
`Platform.getContainerItem`, which is `src/main`, and `src/api` imports nothing from `src/main` anywhere in
this port - the same constraint that put `ISubMenuHost` in `appeng.helpers` (§8.8). So the API carries only
the per-slot fact, and `Platform.getRemainingItem(details, slot, inSlot, cpuSupplied)` combines it with the
container lookup in one place that both `CraftingCPUCluster` and `TileMolecularAssembler` call.

A third default method carries the other half of the contract, on the receiving end:

```java
// appeng.api.implementations.tiles.ICraftingMachine
default boolean acceptsFabricatedContainers();   // false
```

It defaults to false deliberately. A machine written before any of this existed hands every container back
the way a crafting table does, and being handed a fabricated one would have it mint an item out of a fluid
on every craft - so such a machine is passed over and the pattern waits for a molecular assembler, rather
than being pushed somewhere that duplicates. `TileMolecularAssembler` answers true; an addon's machine joins
in by consulting `isContainerFabricated(slot)` and leaving that slot empty when the craft finishes.

`appeng.api.config.FluidSubstitution` joins `ItemSubstitution` as the button's `Settings.ACTIONS` value.

## 9. Implementation class registry

### Wave 1a — `appeng.util` (done)

**Deleted:** `util\item\AEStack`, `AEItemStack`, `ItemList`, `ItemModList`, `ItemVariantList`, `FuzzyItemVariantList`, `NormalItemVariantList`, `MeaningfulItemIterator`, `AEItemStackRegistry`, `AESharedItemStack` (the last two were orphaned once `AEItemStack` went); `util\inv\ItemListIgnoreCrafting`.

**Kept in `util\item`:** `ItemStackHashStrategy` (needed by `ItemEncodedPattern`), `OreDictFilterMatcher`.

```java
// appeng.util.item.OreHelper
public static final OreHelper INSTANCE;
public Optional<OreReference> getOre(ItemStack itemStack);
public boolean sameOre(AEItemKey itemKey, AEItemKey other);
public boolean sameOre(OreReference a, OreReference b);
public boolean sameOre(AEItemKey itemKey, ItemStack o);
public Set<Integer> getMatchingOre(List<OreDictFilterMatcher.MatchRule> rulesList);
public List<ItemStack> getCachedOres(String oreName);

// appeng.util.item.OreReference
public List<AEItemKey> getAEEquivalents();
public Collection<Integer> getOres();

// appeng.util.prioritylist.IPartitionList  (not generic)
boolean isListed(AEKey input);
boolean isEmpty();
Iterable<AEKey> getItems();
default boolean matchesFilter(AEKey key, IncludeExclude mode);
static IPartitionList.Builder builder();
class Builder { void add(AEKey); void addAll(Iterable<AEKey>); void fuzzyMode(FuzzyMode); IPartitionList build(); }

public static final DefaultPriorityList INSTANCE;
public FuzzyPriorityList(KeyCounter in, FuzzyMode mode);
public PrecisePriorityList(KeyCounter in);
public void MergedPriorityList.addNewList(IPartitionList list, boolean isWhitelist);
public OreDictPriorityList(List<OreDictFilterMatcher.MatchRule> oreMatch);

// appeng.util.ItemSorters
public static final Comparator<Object2LongMap.Entry<AEKey>> CONFIG_BASED_SORT_BY_NAME;
public static final Comparator<Object2LongMap.Entry<AEKey>> CONFIG_BASED_SORT_BY_MOD;
public static final Comparator<Object2LongMap.Entry<AEKey>> CONFIG_BASED_SORT_BY_SIZE;
public static final Comparator<Object2LongMap.Entry<AEKey>> CONFIG_BASED_SORT_BY_INV_TWEAKS;
public static void init();
public static int compareLong(long a, long b);
public static void setDirection(SortDir direction);

// appeng.util.inv
// The three below were renamed after the port: their `I` prefixes named the deleted IMEInventory, not an
// interface. All three are unused by the mod and kept for addons.
public MEStorageAdaptor(MEStorage input, IActionSource src);        // was IMEAdaptor, extends InventoryAdaptor
public MEStorageAdaptorIterator(MEStorageAdaptor parent, KeyCounter availableItems);  // was IMEAdaptorIterator
public MEStorageDestination(MEStorage o);                           // was IMEInventoryDestination
public boolean MEStorageDestination.canInsert(ItemStack stack);
// ItemSlot: getAEItemStack/setAEItemStack RENAMED to getGenericStack()/setGenericStack()
public GenericStack ItemSlot.getGenericStack();
public ItemStack ItemSlot.getItemStack();  public void ItemSlot.setItemStack(ItemStack is);
public boolean ItemSlot.isExtractable();   public int ItemSlot.getSlot();  public void ItemSlot.setSlot(int slot);

// appeng.util.iterators.AEInvIterator implements Iterator<GenericStack>
public AEInvIterator(AppEngInternalAEInventory inventory);

// appeng.util.Platform — changed methods
public static String getModId(AEKey key);          // two overloads collapsed into one
public static String getItemDisplayName(Object o); // accepts AEKey or ItemStack
public static String getFluidDisplayName(Object o);
public static List<String> getTooltip(Object o);
public static long poweredExtraction(IEnergySource, MEStorage, AEKey, long amount, IActionSource[, Actionable]);
public static long poweredInsert(IEnergySource, MEStorage, AEKey, long amount, IActionSource[, Actionable]);
public static void postChanges(IStorageService gs, ItemStack removed, ItemStack added, IActionSource src);
public static ItemStack extractItemsByRecipe(IEnergySource, IActionSource, MEStorage src, World, IRecipe,
        ItemStack output, InventoryCrafting, ItemStack providedTemplate, int slot, KeyCounter items,
        Actionable, IPartitionList filter);
// postListChanges(...) removed
```

### Wave 1b — `appeng.me` (done)

**Deleted:** `me\storage\AbstractCellInventory`, `BasicCellInventoryHandler` (merged into `BasicCellInventory`), `NetworkInventoryHandler` (→ `NetworkStorage`), `MEPassThrough` (→ `DelegatingMEInventory`), `MEMonitorPassThrough`, `ItemWatcher`; `me\cache\NetworkMonitor`; `me\helpers\GenericInterestManager`, `MEMonitorHandler`.

```java
// appeng.me.storage.NetworkStorage implements MEStorage   ← the boundary between 1b-1 and 1b-2
public NetworkStorage();
public void mount(int priority, MEStorage inventory);
public void unmount(MEStorage inventory);
public long insert(AEKey what, long amount, Actionable type, IActionSource src);
public long extract(AEKey what, long amount, Actionable mode, IActionSource source);
public void getAvailableStacks(KeyCounter out);
public ITextComponent getDescription();

// appeng.me.storage.DelegatingMEInventory implements MEStorage
public DelegatingMEInventory(MEStorage delegate);
protected MEStorage getDelegate();  protected void setDelegate(MEStorage delegate);

// appeng.me.storage.MEInventoryHandler implements MEStorage
public MEInventoryHandler(MEStorage delegate);
public void setAllowExtraction(boolean);  public void setAllowInsertion(boolean);
public void setWhitelist(IncludeExclude);  public void setPartitionList(IPartitionList);
public void setExtractFiltering(boolean, boolean);  public void setVoidOverflow(boolean);
public boolean isSticky();  public void setSticky(boolean sticky);      // restored, see §10

// appeng.me.storage.BasicCellInventory implements StorageCell
public static StorageCell createInventory(ItemStack, ISaveProvider);
public static boolean isCell(ItemStack);
public boolean isSticky();                                              // derived from the cell's upgrades
// + isPreformatted/isFuzzy/getPartitionListMode/getItemStack/getFuzzyMode/getConfigInventory/
//   getUpgradesInventory/getBytesPerType/canHoldNewItem/getTotal*/getFree*/getStored*/getUsedBytes/getRemaining*

// appeng.me.storage.CreativeCellInventory implements StorageCell
public static StorageCell createInventory(ItemStack);        // was getCell(...)

// appeng.me.storage.DriveWatcher extends MEInventoryHandler
public DriveWatcher(StorageCell cell, Runnable activityCallback);   // constructor changed completely
public CellState getStatus();  public StorageCell getCell();

// appeng.me.storage.NullInventory
public static MEStorage of();

// appeng.me.storage.SecurityStationInventory implements MEStorage
public SecurityStationInventory(TileSecurityStation);
public KeyCounter getStoredItems();

// appeng.me.storage.MEMonitorIInventory / MEMonitorIFluidHandler implements MEStorage, ITickingMonitor
public MEMonitorIInventory(InventoryAdaptor);   public MEMonitorIFluidHandler(IFluidHandler);
public void setMode(StorageFilter);

// appeng.me.helpers.InterestManager<T>
public InterestManager(Multimap<AEKey, T> interests);
public boolean put(AEKey stack, T iw);  public boolean remove(AEKey stack, T iw);
public void setWatchAll(boolean watchAll, T watcher);  public boolean containsKey(AEKey stack);
public Collection<T> get(AEKey stack);  public Collection<T> getAllStacksWatchers();  public boolean isEmpty();

// appeng.me.helpers.StackWatcher<T> implements IStackWatcher
public StackWatcher(InterestManager<StackWatcher<T>> interestManager, T host);
public T getHost();  public void destroy();

// appeng.me.helpers.AENetworkProxy
public IStorageService getStorage();                         // was IStorageGrid

// appeng.me.cluster.implementations.CraftingCPUCluster
public long injectItems(AEKey what, long amount, Actionable mode, IActionSource src);
public GenericStack getFinalOutput();
public void addStorage(AEKey what, long amount);  public void addEmitable(AEKey what, long amount);
public void addListener(ICraftingCPUListener l, Object verificationToken);   // restored, see §10
public void removeListener(ICraftingCPUListener l);

// appeng.me.cluster.implementations.ICraftingCPUListener   (new)
boolean isValid(Object verificationToken);
void onCraftingCPUChange(AEKey what, IActionSource source);
```

### IMPORTANT for wave 2 — the shape of `appeng.crafting` that is already assumed

**Approved by the owner 2026-07-27.** `CraftingGridCache` and `CraftingCPUCluster` are **already written** as though `appeng.crafting` looks like the following. This is not a proposal — it is what the assembled wave 1b code already stands on. Wave 2 must match it exactly or the integration will diverge:

```java
MECraftingInventory(MEStorage, boolean, boolean, boolean);   // plus MEStorage-style insert/extract/getAvailableStacks
CraftingJob(World, IGrid, IActionSource, GenericStack, ICraftingCallback);
GenericStack CraftingLink.injectItems(GenericStack, Actionable);
```

Note on `MECraftingInventory`: the old class had five constructors, two of which are relevant — `(IMEInventory<IAEItemStack>, boolean, boolean, boolean)` and `(IMEMonitor<IAEItemStack>, IActionSource, boolean, boolean, boolean)`. Both source types collapse into `MEStorage`, so the two merge into one and the `IActionSource` parameter disappears. The four-argument form above is the one already used, at `CraftingCPUCluster.java:785`. If a call site turns out to need the action source, **add** a five-argument overload — that is an addition and breaks nothing. Do not change the four-argument form. The three booleans are unchanged: `logExtracted`, `logInjections`, `logMissing`. The old `(IItemList<IAEItemStack>)` constructor becomes `(KeyCounter)`.

One more wave 1b assumption: `CraftingGridCache implements IStorageProvider, MEStorage` and mounts itself into the network at priority `Integer.MAX_VALUE`. This preserves the old trick where the CPU pretends to be the highest-priority storage in order to intercept insertion and detect that a craft has finished. Without it `completeJob()` is never called.

**Debt carried forward** (files outside wave 1's scope, deliberately left broken):

- `AppEngInternalAEInventory.getAEStackInSlot(int)` must return `GenericStack` — `AEInvIterator` depends on it. Wave 2.
- `ItemHandlerAdapter`, `MEMonitorIInventory` call the old `ItemSlot.getAEItemStack()`. Wave 3.
- `TileCharger`, `ContainerOreDictStorageBus` call `((AEItemStack) x).getOre()` → use `OreHelper.INSTANCE`. Waves 2 and 4.
- `appeng.core.api.ApiStorage` must implement the new `IStorageHelper`, delegating to `Platform`. Wave 2.
- `appeng.core.api.ApiClientHelper` is still entirely on the old API and is the file that calls `handler.isSticky()`. Wave 2.
- `appeng.crafting.*` — the shape is fixed above and must not be changed. Wave 2.
- `core\Registration.java:205` — `registerGridCache(IStorageGrid.class, ...)` → `IStorageService.class`. Wave 2.
- `PartStorageBus`, `PartOreDicStorageBus`, `PartFluidStorageBus` — cast to `GridStorageCache` and call the removed `cellUpdate(null)`; replace with `IStorageProvider.requestUpdate(node)`. Also must call `handler.setSticky(true)` when `Upgrades.STICKY` is installed. Waves 3 and 5.
- `PartLevelEmitter`, `PartFluidLevelEmitter` — call the removed `NetworkMonitor.getGridCurrentCount()`; need a channel-free replacement. Waves 3 and 5.
- `TileCraftingTile`, `TileCraftingMonitorTile`, `CraftingCPUStatus`, `PacketCraftingToast` — move to the new `CraftingCPUCluster` signatures. Waves 2 and 4.
- `ContainerCraftingCPU` — implement `ICraftingCPUListener` instead of `IMEMonitorHandlerReceiver<IAEItemStack>`; `cpu.addListener(this, null)` on attach, `cpu.removeListener(this)` on detach. Wave 4.
- `PacketMEInventoryUpdate` is `IAEItemStack`-based and needs an `appendItem(GenericStack)`-shaped overload. Wave 4, affects every terminal.
- `TileDrive` — new `DriveWatcher(StorageCell, Runnable)` constructor. Wave 2.

### Wave 2 — `crafting`, `tile`, `helpers`, `capabilities`, `core` (done)

**Deleted:** `tile\misc\CondenserVoidInventory` (merged into `CondenserItemInventory` — `MEStorage` is not per-channel, so one class covers item plus void-everything-else).

**Created:** `core\api\AEItemKeyType`, `core\api\AEFluidKeyType` — the first concrete key types. The Forge registry is built in `Registration.newRegistry()` (`RegistryEvent.NewRegistry`) and both types are registered inline in that same handler, which is the only construction that provably runs before item registration.

```java
// appeng.core.api.AEItemKeyType extends AEKeyType     — registry name AEKeyTypes.ITEMS_ID
// appeng.core.api.AEFluidKeyType extends AEKeyType    — registry name AEKeyTypes.FLUIDS_ID
//   getAmountPerOperation() = 125, getAmountPerByte() = 8000, getAmountPerUnit() = 1000, getUnitSymbol() = "B"
// Button textures/icons are PLACEHOLDERS (states.png + vanilla chest / water bucket). Wave 4 replaces them.

// appeng.core.api.ApiStorage implements IStorageHelper — delegates entirely to appeng.util.Platform statics

// appeng.core.api.ApiClientHelper implements IClientHelper
public void addCellInformation(StorageCell handler, List<String> lines);   // sticky tooltip preserved

// appeng.core.features.registries.cell.BasicCellHandler / CreativeCellHandler implements ICellHandler
public boolean isCell(ItemStack);
public StorageCell getCellInventory(ItemStack, @Nullable ISaveProvider host);
// getStatusForCell/cellIdleDrain removed — that information lives on the returned StorageCell

// appeng.api.storage.StorageCells — GUI-handler half, added after wave 2 (see §4.3)
public static synchronized void addCellGuiHandler(ICellGuiHandler);
public static synchronized ICellGuiHandler getGuiHandler(AEKeyType keyType);
public static synchronized ICellGuiHandler getGuiHandler(AEKeyType keyType, ItemStack cell);  // prefers isSpecializedFor

// appeng.crafting.MECraftingInventory implements MEStorage
public MECraftingInventory();
public MECraftingInventory(MEStorage target, boolean logExtracted, boolean logInjections, boolean logMissing);
public MECraftingInventory(KeyCounter counter);
public KeyCounter getItemList();   public boolean commit(IActionSource src);

// appeng.crafting.CraftingJob implements ICraftingJob
public CraftingJob(World, IGrid, IActionSource, GenericStack what, ICraftingCallback);
public void populatePlan(KeyCounter plan);                        // frozen interface method — MERGES the two
public void populatePlan(KeyCounter used, KeyCounter requestable); // additive, full fidelity — see §9.2
public GenericStack getOutput();

// appeng.crafting.CraftingLink
public GenericStack injectItems(GenericStack input, Actionable mode);

// appeng.crafting.CraftingTreeProcess / CraftingTreeNode
public void addContainers(GenericStack container);
public CraftingTreeNode(ICraftingGrid, CraftingJob, AEKey wat, CraftingTreeProcess par, int slot, int depth);

// appeng.crafting.CraftBranchFailure / CraftingCalculationFailure
public CraftBranchFailure(AEKey what, long howMany);

// appeng.helpers.DualityInterface — now implements MEStorage directly (was IStorageMonitorable)
public MEStorage getInventory();
public MEStorage getMonitorable(IActionSource src, MEStorage myInterface);
public GenericStack injectCraftedItems(ICraftingLink link, GenericStack items, Actionable mode);
public void onStackReturnedToNetwork(GenericStack stack);
public GenericStack getUnlockStack();

// appeng.helpers.IInterfaceHost
default void onStackReturnNetwork(GenericStack stack);

// appeng.helpers.MultiCraftingTracker
public boolean handleCrafting(int x, long itemToCraft, AEKey what, InventoryAdaptor d, World w,
        IGrid g, ICraftingGrid cg, IActionSource mySrc);

// appeng.helpers.PatternHelper implements ICraftingPatternDetails
GenericStack[] getInputs()/getCondensedInputs()/getCondensedOutputs()/getOutputs();
List<GenericStack> getSubstituteInputs(int slot);

// appeng.helpers.WirelessTerminalGuiObject implements IPortableCell
public MEStorage getInventory();    // returns this
// NOTE: addListener/removeListener were dropped here — see §10 "Third case", wave 4 must resolve

// appeng.capabilities.NullMENetworkAccessor
public MEStorage getInventory(IActionSource src);

// appeng.tile.inventory.AppEngInternalAEInventory
public GenericStack getAEStackInSlot(int slot);
public Iterator<GenericStack> getNewAEIterator();

// appeng.tile.inventory.AppEngCellInventory
public void setHandler(int slot, StorageCell handler);

// appeng.tile.inventory.AppEngNetworkInventory
public AppEngNetworkInventory(Supplier<IStorageService> networkSupplier, IActionSource source,
        IAEAppEngInventory inventory, int size, int maxStack);

// appeng.tile.crafting.TileCraftingMonitorTile
public void setJob(GenericStack is);   public GenericStack getJobProgress();

// appeng.tile.misc.TileInterface
public void onStackReturnNetwork(GenericStack stack);
public GenericStack injectCraftedItems(ICraftingLink link, GenericStack items, Actionable mode);

// appeng.tile.misc.TileSecurityStation / storage.TileChest
public MEStorage getInventory();                             // ITerminalHost
public void mountInventories(IStorageMounts storageMounts);  // TileChest + TileDrive, via IStorageProvider
```

Notes from wave 2 that later waves depend on:

- **A cell's key type** is read as `is.getItem() instanceof IBasicCellItem c ? c.getKeyType() : AEKeyType.items()`. The creative cell item does not implement `IBasicCellItem` yet — wave 3 owns it.
- **`TileChest` security gating** is preserved by an in-file `SecurityAwareCellStorage` wrapper around `DriveWatcher`, because `MEStorage` has no permission hook. Machine-sourced access still bypasses it, as before.
- **Cell status lights** map `DriveWatcher.getStatus()` → `CellState` → the old 0–4 `DriveSlotState` scale, duplicated in `TileDrive` and `TileChest` because the rendering side (`appeng.block`) is untouched.
- **`ICellGuiHandler.isSpecializedFor(ItemStack)` was restored** after wave 2 dropped it. It is a `default false` addon extension point — an addon shipping a cell with its own screen overrides it. `StorageCells.getGuiHandler(AEKeyType, ItemStack)` honours it; `TileChest.openGui()` calls that overload.
- **The GUI-handler registry lives in `StorageCells`** (api), not in `appeng.core.features.registries.cell`. Wave 2 landed it as a `src/main`-only class because the frozen `StorageCells` covered only `ICellHandler` and upstream AE2 has no GUI-handler concept at all; that left an addon able to implement the api interface but forced to import an internal class to register it. Resolved by the owner after wave 2 — `addCellGuiHandler`/`getGuiHandler` moved onto `StorageCells` and `CellRegistry` was deleted. **This is a deliberate divergence from upstream** and one of the few additions to the otherwise frozen §4 surface.

### Wave 3b — `appeng.parts.misc` and `appeng.parts.reporting` (done)

Nine files: `PartStorageBus`, `PartOreDicStorageBus`, `PartInterface`, `ItemHandlerAdapter`, `ItemRepositoryAdapter` (all `appeng.parts.misc`); `AbstractPartTerminal`, `AbstractPartMonitor`, `AbstractPartEncoder`, `PartConversionMonitor` (all `appeng.parts.reporting`). One new file: `appeng.parts.misc.InitExternalStorageStrategies`.

This wave built the `appeng.api.behaviors.ExternalStorageStrategy` consumer side that §3/§3.1 specified but nothing implemented yet, and is the reason `PartStorageBus` no longer hand-resolves `IItemHandler` for items only.

```java
// appeng.parts.misc.ItemHandlerAdapter implements MEStorage, ITickingMonitor    — package-private
ItemHandlerAdapter(IItemHandler itemHandler, boolean extractableOnly, @Nullable Runnable changeListener);
// insert/extract loop over the wrapped IItemHandler exactly like the old injectItems/extractItems did, but
// return a plain `long` (no more IAEItemStack "leftover" object); getAvailableStacks() serves a KeyCounter
// cache rebuilt in the constructor, after every MODULATE insert/extract, and once per onTick() (which also
// runs changeListener.run() and reports URGENT/SLOWER by diffing the cache against its previous contents).
// changeListener replaces the old per-listener IMEMonitorHandlerReceiver posting: it is invoked exactly once
// per real (non-simulated) mutation, and PartStorageBus wires it to `getProxy().getTick().alertDevice(...)`.

// appeng.parts.misc.ItemHandlerAdapter.Strategy implements ExternalStorageStrategy   — package-private, static nested
Strategy(World world, BlockPos fromPos, EnumFacing fromSide);
// createWrapper(extractableOnly, callback) re-resolves the IItemHandler capability at `fromPos`/`fromSide`
// fresh every call (target may not exist, may have changed) and wraps it in a new ItemHandlerAdapter, or
// returns null if there is nothing there. This is the ExternalStorageStrategy for AEKeyType.items(); the
// fluid equivalent (wave 5) and any addon's are expected to follow the exact same shape.

// appeng.parts.misc.InitExternalStorageStrategies   — NEW, public
public static void register();
// ExternalStorageStrategy.register(AEKeyType.items(), ItemHandlerAdapter.Strategy::new); nothing else.
// Called from appeng.core.Registration (agent 3-4's file; already wired up as of that wave's edit, see §9's
// wave 3d entry) after the AEKeyType Forge registry is populated.

// appeng.parts.misc.ItemRepositoryAdapter implements MEStorage, ITickingMonitor   — package-private
ItemRepositoryAdapter(IItemRepository itemRepository, @Nullable Runnable changeListener);
// Same shape as ItemHandlerAdapter but wraps AE2UD's fork-specific IItemRepository (Storage Drawers; see
// CONTRACT.md §10/§11). Not registered through ExternalStorageStrategy — the capability is not keyed by
// AEKeyType at all — instead PartStorageBus checks for it explicitly as an extra, exactly like the old code.

// appeng.parts.misc.PartStorageBus extends PartUpgradeable implements IGridTickable, IStorageProvider, IPriorityHost
public MEInventoryHandler getInternalHandler();       // was MEInventoryHandler<IAEItemStack>; unaffected callers: wave 4's ContainerStorageBus/ContainerOreDictStorageBus
public void mountInventories(IStorageMounts mounts);  // replaces the deleted ICellContainer#getCellArray
protected IPartitionList createFilter();              // NEW, protected — factored out so PartOreDicStorageBus only overrides the filter contents, not the whole handler-rebuild method
MEStorage findExternalStorage(TileEntity target, EnumFacing targetSide, boolean extractableOnly, Runnable changeListener);
// Resolution order, all preserved from the pre-migration code: (1) IStorageMonitorableAccessor (direct link
// to another ME network / storage-bus-on-interface), (2) IItemRepository (fork-specific, see above), (3) the
// generic ExternalStorageStrategy registry via StackWorldBehaviors.createExternalStorageStrategies, composed
// by a private CompositeExternalStorage when more than one AEKeyType answers (only items today; from wave 5
// onward the same bus serves items and fluids simultaneously with no further change to this file).
// `handler` is now one stable MEInventoryHandler instance for the part's whole lifetime (mirroring
// AE2-original's StorageBusPart.StorageBusInventory) instead of a fresh object every rebuild: settings
// changes (ACCESS/STORAGE_FILTER/FUZZY_MODE/Upgrades.INVERTER/CAPACITY/FUZZY/STICKY) just update its flags
// in place, and IStorageProvider.requestUpdate(node) is only called when the registration-worthy state
// actually flips (NullInventory ↔ a real target) or the priority setter runs — not on every settings change.

// appeng.parts.misc.PartOreDicStorageBus extends PartStorageBus
// No longer overrides getInternalHandler() at all (previously ~70 duplicated lines) — only createFilter()
// is overridden, returning the ore-dictionary OreDictPriorityList instead of the config-slot-built one.
// ACCESS/STORAGE_FILTER/Upgrades.INVERTER/Upgrades.STICKY/priority are shared, unchanged, with the base class.

// appeng.parts.misc.PartInterface
// Dropped `implements IStorageMonitorable` (interface deleted) and its `getInventory(IStorageChannel<T>)`
// override — DualityInterface already implements MEStorage directly and exposes itself through
// IStorageMonitorableAccessor via its own capability handler (both wave 2), so there was nothing left for
// PartInterface itself to provide. Dropped the `onStackReturnNetwork(IAEItemStack)` override too — it is now
// a `default` method on IInterfaceHost itself, already forwarding to the right place.
public GenericStack injectCraftedItems(ICraftingLink link, GenericStack items, Actionable mode);   // was IAEItemStack

// appeng.parts.reporting.AbstractPartTerminal implements ITerminalHost, ...
public MEStorage getInventory();   // was <T extends IAEStack<T>> IMEMonitor<T> getInventory(IStorageChannel<T>)

// appeng.parts.reporting.AbstractPartEncoder
// GenericStack.wrapInItemStack(GenericStack) replaces IAEItemStack.createItemStack() for the crafting/output
// preview slots — deliberately the generic wrapper (not an AEItemKey-only cast), so a processing pattern's
// fluid ingredients (Upgrades.PATTERN_EXPANSION-adjacent fork feature, EXPANDED_PROCESSING_PATTERN_TERMINAL,
// see §10) still display correctly once wave 5 lands, with no further change to this file.

// appeng.parts.reporting.AbstractPartMonitor extends AbstractPartDisplay implements IPartStorageMonitor, IStorageWatcherNode
// Collapsed the old split configuredItem/configuredFluid fields into a single `AEKey configuredKey` — a
// type-erased key covers both variants (and any future one) uniformly, which the old per-channel IAEStack
// subclasses could not. NBT/packet (de)serialisation now goes through AEKey.toTagGeneric/fromTagGeneric and
// AEKey.writeOptionalKey/readOptionalKey instead of two separate AEItemStack/AEFluidStack (de)serialisers.
public GenericStack getDisplayed();                 // was IAEStack<?>
protected final AEKey getConfiguredKey();           // NEW, protected — the bare key, for subclasses (below) that need it without the amount getDisplayed() also carries
public void onStackChange(AEKey what, long amount); // was onStackChange(IItemList<?>, IAEStack<?>, IAEStack<?>, IActionSource, IStorageChannel<?>)
// configureWatchers() now reads getProxy().getStorage().getCachedInventory().get(key) — the network's own
// per-tick cache (IStorageService#getCachedInventory, restored by the wave-1a review, §8.3's neighbour) —
// instead of calling findPrecise() on a per-channel IMEMonitor's IItemList.

// appeng.parts.reporting.PartConversionMonitor extends AbstractPartMonitor
// insertItem/extractItem/drainFluidContainer/fillFluidContainer rewritten around AEItemKey/AEFluidKey and
// Platform.poweredInsert/poweredExtraction's new `long` (amount actually moved) return value, replacing the
// old "returns the leftover IAEItemStack/IAEFluidStack" contract everywhere those two methods were called.
```

**Sticky Card, ACCESS/STORAGE_FILTER/FUZZY_MODE, Upgrades.INVERTER/CAPACITY/FUZZY, the ore-dictionary mechanic, Upgrades.PATTERN_EXPANSION** — all audited against the pre-migration file and confirmed present, see the `PartStorageBus`/`PartOreDicStorageBus`/`PartInterface`/`AbstractPartEncoder` notes above.

**Could not be literally preserved — reported per Rule 6, not silently dropped:** the pre-migration `PartStorageBus.updateSetting` compared the old vs. new `Settings.ACCESS` value specifically to replay one last full listing through `IStorageService#postAlterationOfStoredItems` *using the old (more permissive) access* before applying a stricter new one, so watchers got a proper "these items just disappeared" notification instead of the bus's contribution silently vanishing mid-cache. That method does not exist on the new `IStorageService` (§4.3) — there is no per-source posting entry point left at all, because `MEStorage` is not a per-listener monitor any more and the network now diffs its own cached view once per tick (`GridStorageCache.updateCachedStacks`, §8.3) instead of being pushed to. **Best-effort equivalent implemented:** every settings change (not just ACCESS) forces a full rebuild of `handler`'s flags via `resetCache(true)`, and any change that flips whether this bus contributes anything at all triggers `IStorageProvider.requestUpdate`, so the network re-derives its cache — and any watcher sees the bus's contents disappear — on the very next tick, centrally, instead of through a per-bus push. The visible *result* for a player is unchanged; the old *mechanism* that produced it no longer exists to be mirrored literally. Flagged here per Rule 6 rather than left silent.

**Debt handed to wave 4 (`appeng.client.render`):** `AbstractPartMonitor.renderDynamic` calls `TesrRenderHelper.renderItem2dWithAmount`/`renderFluid2dWithAmount` as if their signatures were `(AEItemKey, long amount, float, float)`/`(AEFluidKey, long amount, float, float)`. The actual file (`appeng.client.render`, wave 4 scope, untouched here) still has the pre-migration `(IAEItemStack, float, float)`/`(IAEFluidStack, float, float)` signatures — those types no longer exist, so this was never going to compile against the old file regardless of how this wave wrote it. Wave 4 must update the two methods to take a key and an amount separately, mirroring every other forward reference already listed as debt in this file (e.g. wave 2's `TileCharger`/`ContainerOreDictStorageBus` entry).

**Debt handed to wave 4 (`appeng.container`):** `ContainerStorageBus`/`ContainerOreDictStorageBus`/`ContainerFluidStorageBus` still call `this.storageBus.getInternalHandler()` and treat the result as `IMEInventory<IAEItemStack>`/`getAvailableItems(IItemList<IAEItemStack>)`. Not touched here (`appeng.container` is wave 4 per §7) — the new `getInternalHandler()` returns a plain `MEInventoryHandler` (a `MEStorage`); wave 4's `partition()`-style code should call `getAvailableStacks()` (a `KeyCounter`) and wrap each key with `AEKey.wrapForDisplayOrFilter()`/`GenericStack.wrapInItemStack` for the config slots, the same pattern wave 3c/3d already used elsewhere.

Audited for the `GenericStack.equals()` hazard (§9.1): `PartConversionMonitor`'s wrench/fluid/item match checks (`onPartActivate`) and its inventory-scan match checks (`insertItem`'s `allItems` branch) all use `AEItemKey.matches(ItemStack)`/`AEFluidKey.matches(FluidStack)`, never `GenericStack.equals()` or a whole-`GenericStack` comparison — this is exactly the hazard case (the old `IAEStack` subclasses' `equals(ItemStack/FluidStack)` overloads ignored size) and every instance found was translated to the size-insensitive key-level check.

### Wave 3a — `appeng.parts.automation` (done)

Rewrote the six part classes named in the brief (`PartImportBus`, `PartExportBus`, `PartAnnihilationPlane`,
`PartAbstractFormationPlane`, `PartFormationPlane`, `PartLevelEmitter`) plus a small, necessary companion
(`PartIdentityAnnihilationPlane` — not in the brief's file list, but it subclasses `PartAnnihilationPlane`
and overrode two methods that no longer exist on the part once pickup moved into a strategy object; leaving
it untouched would have silently dropped the whole "Identity Annihilation Plane" mechanic, which rule 6
forbids). Created the whole item strategy layer plus the registration entry point.

**New classes (all package-private except `InitStackWorldBehaviors`, which must be public for
`appeng.core.Registration` to call):**

```java
// appeng.parts.automation.StackTransferContextImpl implements StackTransferContext
StackTransferContextImpl(MEStorage internalStorage, IEnergySource energySource, IActionSource actionSource,
        int operationsRemaining, IPartitionList filter, @Nullable FuzzyMode fuzzyMode);
boolean hasDoneWork();                 // initialOperations > operationsRemaining, for TickRateModulation
IPartitionList getPartitionList();     // extension beyond the frozen interface, see note below
@Nullable FuzzyMode getFuzzyMode();    // extension beyond the frozen interface
IEnergySource getEnergySource();       // extension beyond the frozen interface

// appeng.parts.automation.HandlerStrategy — thin AEKeyType.items() <-> InventoryAdaptor association
static final HandlerStrategy ITEMS;
AEKeyType getKeyType();  boolean isSupported(AEKey what);

// appeng.parts.automation.StorageImportStrategy implements StackImportStrategy
StorageImportStrategy(World world, BlockPos fromPos, EnumFacing fromSide);
static StackImportStrategy createItem(World world, BlockPos fromPos, EnumFacing fromSide);

// appeng.parts.automation.StorageExportStrategy implements StackExportStrategy
StorageExportStrategy(World world, BlockPos fromPos, EnumFacing fromSide);
static StackExportStrategy createItem(World world, BlockPos fromPos, EnumFacing fromSide);

// appeng.parts.automation.StackImportFacade / StackExportFacade / PlacementStrategyFacade
// — iterate/dispatch over List<StackImportStrategy>, List<StackExportStrategy>,
//   Map<AEKeyType, PlacementStrategy> respectively. Ported verbatim from AE2-original.

// appeng.parts.automation.ItemPickupStrategy implements PickupStrategy
ItemPickupStrategy(World world, BlockPos pos, EnumFacing side, TileEntity host,
        Map<Enchantment, Integer> enchantments, @Nullable UUID ownerUuid);
protected float calculateEnergyUsage(WorldServer w, BlockPos pos, List<ItemStack> items);   // overridable
protected List<ItemStack> obtainBlockDrops(WorldServer w, BlockPos pos);                    // overridable

// appeng.parts.automation.IdentityItemPickupStrategy extends ItemPickupStrategy
IdentityItemPickupStrategy(World world, BlockPos pos, EnumFacing side, TileEntity host,
        Map<Enchantment, Integer> enchantments, @Nullable UUID ownerUuid);
// overrides calculateEnergyUsage (x16) and obtainBlockDrops (always the silk-touch result) — this is
// exactly what PartIdentityAnnihilationPlane used to override directly on the part.

// appeng.parts.automation.ItemPlacementStrategy implements PlacementStrategy
ItemPlacementStrategy(World world, BlockPos pos, EnumFacing fromSide, TileEntity host, @Nullable UUID ownerUuid);

// appeng.parts.automation.InitStackWorldBehaviors — PUBLIC, the wave's entry point
public static void register();   // items only: import, export, placement, pickup. No ExternalStorageStrategy
                                  // (that belongs to agent 3-2's appeng.parts.misc.InitExternalStorageStrategies).

// appeng.parts.automation.PartAbstractFormationPlane extends PartUpgradeable
//         implements IStorageProvider, IPriorityHost, MEStorage   — no longer generic over IAEStack<T>
protected abstract AEKeyType getKeyType();
protected abstract AppEngInternalAEInventory getConfigInventory();
protected int getFilterSlotsInUse();                     // default 18 + Upgrades.CAPACITY*9
protected final PlacementStrategy getPlacementStrategies();
protected boolean matchesConfiguredFilter(AEKey what);
public abstract long insert(AEKey, long, Actionable, IActionSource);   // left abstract, like the old injectItems()
// extract()->0, getAvailableStacks()->no-op, getDescription()->getItemStackRepresentation().getDisplayName()

// appeng.parts.automation.PartFormationPlane extends PartAbstractFormationPlane
// getKeyType() = AEKeyType.items(); config is still a 63-slot AppEngInternalAEInventory (unchanged size).
```

**Why `StackTransferContext` needed extra, non-frozen accessors on `StackTransferContextImpl`.** AE2UD's
frozen `StackTransferContext` (CONTRACT.md §3) is deliberately smaller than AE2-original's: it has no
`getEnergySource()`, no `isInFilter()`/`isKeyTypeEnabled()`. But the pre-port `PartImportBus`/`PartExportBus`
charged AE power for every network access (`Platform.poweredInsert`/`poweredExtraction`) and drove a
per-slot configured-filter loop — both real, currently-live mechanics, not something introduced this wave.
Since `StackTransferContextImpl` is an implementation class in `main` (not part of the frozen surface), it
exposes `getEnergySource()`/`getPartitionList()`/`getFuzzyMode()` as **additional** package-private methods
beyond the interface; `StorageImportStrategy`/`StorageExportStrategy` cast their `StackTransferContext`
parameter to the concrete type to reach them. This is the same pattern earlier waves used to restore
mechanics that didn't fit a frozen shape verbatim (§10, "Restored regressions") — nothing on the frozen
interface changed, no api file was touched.

**Contradicted CONTRACT.md — since resolved by amending the api, see §8.4.** §3's note on
`PickupStrategy.Factory` claimed fortune and silk touch were "the only two enchantments the annihilation
plane actually reads." That was false: AE2UD's energy-cost formula (`ItemPickupStrategy#calculateEnergyUsage`,
ported unchanged from the pre-port `PartAnnihilationPlane`) also reads Efficiency and Unbreaking. The wave 3a
agent kept the mechanic by having `PartAnnihilationPlane` bypass the registry and build its
`ItemPickupStrategy` directly from the full enchantment map. That preserved behaviour but left the
*registered* item strategy lossy, so an addon reaching the plane through
`StackWorldBehaviors.createPickupStrategies` would have got the wrong energy model. The factory now carries
the whole map and the bypass is gone.

**Item-only exception to "no item-specific logic in the bus parts": craft-on-demand.** `PartExportBus`'s
craft-on-demand path still calls `MultiCraftingTracker.handleCrafting(int, long, AEKey, InventoryAdaptor, ...)`
— that signature is frozen from wave 1b/2 (CONTRACT.md §9, "IMPORTANT for wave 2") and needs an
`InventoryAdaptor` to pre-check whether the destination can accept the crafted result before a job starts.
Autocrafting itself is entirely item/`GenericStack`-based in this codebase already (§4.4: aligning the
crafting API is out of scope for v1), so `PartExportBus` obtains that adaptor the same way
`PartSharedItemBus` always did (`getHandler()`) purely for that one pre-check; the actual push once a craft
completes (`injectCraftedItems`) goes through the generic `StackExportStrategy.push(...)`, not the adaptor.
This is not a regression — it is the shape wave 1b/2 already committed to.

**`StorageImportStrategy`/`StorageExportStrategy`/`HandlerStrategy` are adapted, not literal ports.**
AE2-original's versions talk to the adjacent block through a raw `Storage<ItemVariant>`/`ResourceHandler`
capability. Doing the same in 1.12.2 via the bare `IItemHandler` capability would have silently dropped a
mechanic the pre-port `PartImportBus`/`PartExportBus` already had for free: `appeng.util.InventoryAdaptor`
also transparently supports Storage Drawers' `IItemRepository` capability
(`appeng.util.inv.AdaptorItemRepository`) alongside plain `IItemHandler`. `StorageImportStrategy`/
`StorageExportStrategy` therefore wrap `InventoryAdaptor.getAdaptor(...)` (exactly how the pre-port classes
reached the adjacent inventory) instead of a raw capability, preserving that integration. `HandlerStrategy`
still exists (as the brief asked) but is a thin `AEKeyType`/support-check association rather than the
generic `<C, S>` conversion layer upstream has — there is only one handler kind to support this wave (items;
fluids is wave 5's own file).

**Formation plane split preserved, not merged.** AE2-original merged item and fluid formation planes into
one `FormationPlanePart` now that `MEStorage` is type-erased. AE2UD keeps them as separate part
items/blocks (matching the pre-port shape and `appeng.items.parts.PartType`'s registration, which is
outside this wave's scope to change), so `PartAbstractFormationPlane` is non-generic but still
single-key-type per instance: each concrete plane declares its own `getKeyType()` and `insert()` rejects
any other type. **Wave 5 requirement:** `appeng.fluids.parts.PartFluidFormationPlane` currently extends the
deleted generic `PartAbstractFormationPlane<IAEFluidStack>`. It must be changed to extend the new
non-generic `PartAbstractFormationPlane`, implement `getKeyType()` returning `AEKeyType.fluids()`, supply its
own `getConfigInventory()`, and implement `insert(...)` with its own fluid placement logic (block/entity
placement is inherently fluid-shaped, unlike the item template in `PartFormationPlane`).

**Minor, deliberate behavioural simplifications (not mechanic losses):**
- `PickupStrategy.pickUpEntity`'s frozen contract is "true if the entity was consumed" (not "true if
  anything changed", which is what the pre-port `storeEntityItem` returned). `ItemPickupStrategy` follows
  the frozen contract's wording; the visual transition-effect packet in `PartAnnihilationPlane` therefore
  only fires on full pickup, not on a partial-overflow shrink. The storage mechanic itself (partial
  insertion, shrinking the entity's stack) is unchanged.
- `PartImportBus`'s old `isSleeping()` override (`getHandler() == null || super.isSleeping()`) is gone,
  because the bus no longer holds a bus-wide `InventoryAdaptor` field to null-check; `canDoBusWork()`'s
  per-tick chunk-loaded gate (unchanged) already prevents wasted work each tick, so this only affects how
  eagerly the node goes to sleep as an optimisation, not correctness.
- Import-bus per-slot filter order became enumeration order over an `IPartitionList`/`KeyCounter` instead of
  literal config-slot order (both drain fully whenever there's enough operation budget in a tick; this only
  matters when the budget runs out mid-way, a cosmetic scheduling nuance).

**§9.1 hazard, audited:** no whole-`GenericStack` comparisons anywhere in this wave's files; every identity
check compares `AEKey`s (`AEItemKey.of(...)`, `.equals(...)`, `AEItemKey.is(...)`) or uses
`GenericStack.what()`/`.amount()` explicitly where both are meant.

### Wave 3c — `appeng.items.storage`, `appeng.items.contents`, `appeng.recipes` (done)

Six files: `AbstractStorageCell`, `BasicItemStorageCell`, `ItemCreativeStorageCell`, `ItemViewCell` (all `appeng.items.storage`), `PortableCellViewer` (`appeng.items.contents`), `DisassembleRecipe` (`appeng.recipes.game`).

```java
// appeng.items.storage.AbstractStorageCell — no longer generic (dropped <T extends IAEStack<T>>)
public abstract class AbstractStorageCell extends AEBaseItem implements IBasicCellItem, IItemGroup
// isBlackListed(ItemStack, AEKey) — was isBlackListed(ItemStack, T)
// getKeyType() stays abstract, implemented per key type by subclasses

// appeng.items.storage.BasicItemStorageCell extends AbstractStorageCell  (was AbstractStorageCell<IAEItemStack>)
public AEKeyType getKeyType() { return AEKeyType.items(); }   // was getChannel() -> IStorageChannel<IAEItemStack>

// appeng.items.storage.ItemCreativeStorageCell implements IBasicCellItem   (was ICellWorkbenchItem only)
public AEKeyType getKeyType() { return AEKeyType.items(); }   // fixes the wave-2-deferred gap, see below
public int getBytes(ItemStack)/getBytesPerType(ItemStack)/getTotalTypes(ItemStack); public double getIdleDrain();
// declared to satisfy the interface; CreativeCellInventory never reads them (its capacity is unlimited by
// design), values chosen to read as "unlimited" (Integer.MAX_VALUE bytes/types, 0 idle drain) if anything
// ever does query them (e.g. a future cell-workbench display)

// appeng.items.storage.ItemViewCell — unchanged public shape except createFilter
public static AEKeyFilter createFilter(ItemStack[] list);   // was IPartitionList<IAEItemStack>; NEVER returns
// null — AEKeyFilter.all() stands in for the old "no filter installed" null. See the method's javadoc
// in the file itself for the full rationale and the note to waves 4/5 below.

// appeng.items.contents.PortableCellViewer extends DelegatingMEInventory implements IPortableCell, IInventorySlotAware
// (was MEMonitorHandler<IAEItemStack>). Public constructor unchanged: PortableCellViewer(ItemStack is, int slot).
public MEStorage getInventory();   // returns this — ITerminalHost
// injectItems/extractItems overrides (and their notifyListenersOfChange calls) are GONE — there is nothing
// to override any more, since MEStorage's insert/extract are forwarded as-is by DelegatingMEInventory. See
// the live-update note below.

// appeng.recipes.game.DisassembleRecipe — no public signature change; internals only
// getOutput(...) now empties-checks via StorageCells.getCellInventory(stackInSlot, null).getAvailableStacks().isEmpty()
// instead of the deleted IMEInventory<IAEItemStack>/IItemList<IAEItemStack> pair.
```

**Post-merge creative-cell correction.** `ItemCreativeStorageCell` must not implement `IBasicCellItem`:
`BasicCellHandler` is registered first and would claim it before `CreativeCellHandler`. The content type is
instead an additive default on `ICellWorkbenchItem`, overridden by the creative item. AE2UD registers item
and fluid creative-cell variants backed by the same generic `CreativeCellInventory`; the Cell Workbench,
ME Chest and IO Port read the declared type through `ICellWorkbenchItem#getKeyType()`.

**`ItemViewCell.createFilter` return type — the decision waves 4/5 need to know.** The task brief called for `AEKeyFilter`; the api-level `AEKeyFilter` was chosen over the already-multi-type-capable `appeng.util.prioritylist.IPartitionList` (which wave 1 had already turned into a non-generic, `AEKey`-based type — it would have satisfied "works for every key type" too) because `AEKeyFilter` is the frozen, addon-facing type whose javadoc states it "Replaces the various per-channel filter interfaces", i.e. it is the intended long-term home for exactly this kind of predicate. Internally `createFilter` is unchanged: it still builds an `IPartitionList`/`MergedPriorityList` (the same fuzzy/inverter/merge machinery `BasicCellInventory` uses for a cell's own partition list) and exposes it as an `AEKeyFilter` via `list::isListed` (a method reference is valid regardless of the method's name, only its signature has to match `AEKeyFilter.matches(AEKey)`, which `isListed(AEKey)` does).

The friction this creates: `appeng.util.Platform.extractItemsByRecipe(..., IPartitionList filter)` (frozen since wave 1) is fed the result of `ItemViewCell.createFilter(...)` directly at three call sites — `appeng.container.slot.SlotCraftingTerm`, `appeng.container.implementations.ContainerPatternEncoder`, `appeng.core.sync.packets.PacketJEIRecipe` — all three in wave 4's scope and already broken by unrelated deleted types (`IMEMonitor`, `IStorageGrid`, `IItemList<IAEItemStack>`), so they need a full rewrite regardless of this choice. When wave 4 gets there: either give `Platform.extractItemsByRecipe` an additional `AEKeyFilter`-typed overload (additive, does not break the four-argument `IPartitionList` form), or adapt at the call site. Do **not** try to make `IPartitionList` and `AEKeyFilter` interchangeable by retrofitting inheritance between them — `IPartitionList` carries `isEmpty()`/`getItems()` that an arbitrary `AEKeyFilter` cannot generally supply.

**`ItemViewCell.createFilter` never returns null.** Empty/no-op input now yields `AEKeyFilter.all()` instead of `null`, so every caller's old `if (filter != null && !filter.isListed(x))` collapses to `if (!filter.matches(x))`. This is a call-site simplification available to whichever wave rewrites each caller; it is not itself a behaviour change (`AEKeyFilter.all()` matches everything, the same as skipping the check the old null did).

**`PortableCellViewer` gave up its own live-update push** (the `injectItems`/`extractItems` overrides and their `notifyListenersOfChange` calls) because the `IMEMonitor`/`MEMonitorHandler` listener layer they used no longer exists anywhere in the codebase. This is deliberate, not a silent cut: CONTRACT.md §10 ("Third case: terminal live updates") already identifies this exact gap — "Portable cell / view-only cell terminals ... There is no replacement yet ... Wave 4 needs a small push interface on this path" — and wave 2 already hit the same wall in `WirelessTerminalGuiObject`, which forwards `insert`/`extract` with no notification step either. `PortableCellViewer` now matches that established precedent instead of inventing a second, different stopgap. Reads/writes to a portable cell's contents work; a terminal watching a portable cell open in a player's hand will not refresh live until wave 4 designs the replacement push interface, exactly as already flagged.

**Debt handed to wave 5 (fluids):** `appeng.fluids.items.BasicFluidStorageCell extends AbstractStorageCell<IAEFluidStack>` still uses the old generic form and overrides the now-removed `getChannel()`. It was already broken (uses deleted `IAEFluidStack`/`IStorageChannel`) and out of this wave's scope (`appeng.fluids` is wave 5 in its entirety per §7). Wave 5 must change it to `extends AbstractStorageCell` and replace `getChannel()` with `public AEKeyType getKeyType() { return AEKeyType.fluids(); }`, mirroring `BasicItemStorageCell` exactly.

**Audited for the `GenericStack.equals()` hazard (§9.1):** none of the six files compared whole stacks for identity; `ItemViewCell`/`BasicCellInventory`-style partition building always keyed off `AEKey`, never a `GenericStack`, so there was nothing to fix here.

**Nothing implemented against `GenericStack.Wrapper`.** `ItemViewCell.keyOf`/`ItemCreativeStorageCell` decode a config slot's `ItemStack` defensively via `GenericStack.isWrapped`/`unwrapItemStack` (mirroring `appeng.core.api.ApiClientHelper`'s wave-2 `keyOf` helper), but the wrapper item itself still does not exist anywhere in `src/main` — confirmed by grep, and `appeng.core.Registration.java:222-224` still carries the wave-2 `// TODO wave 3: once the GenericStack.Wrapper item ... exists` comment un-actioned. Creating that item is out of this wave's assigned file list (it would live under `appeng.items.misc` and be wired up in `appeng.core.Registration`, both explicitly other agents' territory this wave), so it is reported here rather than attempted: **whichever wave creates it must also flip the `Registration.java` TODO to a real `GenericStack.setWrapper(...)` call**, at which point view cells and the creative-cell tooltip transparently start supporting non-item keys with no further change to the files in this entry.

### Wave 3d — `appeng.items.misc`, `appeng.items.tools.powered`, registration (done)

Four rewritten files (`ItemEncodedPattern`, `ToolPortableCell`, `ToolColorApplicator`, `ToolMatterCannon`), one new file (`WrappedGenericStack`), and edits to the two files this wave owns exclusively (`appeng.core.Registration`, `appeng.core.api.definitions.ApiItems`).

```java
// appeng.items.misc.WrappedGenericStack extends AEBaseItem implements GenericStack.Wrapper   — NEW
// The 1.12.2 implementation of the GenericStack.Wrapper SPI (CONTRACT.md §1.5 / §8 item 3 / §8.1 item 2).
public WrappedGenericStack();                              // setMaxStackSize(1)
public ItemStack wrap(AEKey what, long amount);             // new ItemStack(this) + GenericStack.writeTag
public boolean isWrapped(ItemStack stack);                  // stack.getItem() == this
public GenericStack unwrap(ItemStack stack);                // GenericStack.readTag, null if unreadable
public String getItemStackDisplayName(ItemStack stack);     // wrapped key's getDisplayName()
public void addCheckedInformation(...);                     // "<name>: <formatted amount>" tooltip line
protected void getCheckedSubItems(...);                      // no-op — never listed in any creative tab
public void onUpdate(ItemStack stack, World world, Entity entity, int itemSlot, boolean isSelected);
// ^ sweep hook: scans player.inventory by stack identity (same pattern ItemEncodedPattern.clearPattern
//   uses) and clears the slot if this item is ever found there. Registered via Item#onUpdate, the same
//   per-tick hook appeng.items.tools.powered.ToolWirelessTerminal already used for its magnet logic —
//   confirmed as the correct 1.12.2 equivalent of a "runs every tick while an ItemStack sits in a player's
//   main inventory" callback.

// appeng.core.api.definitions.ApiItems — one new registration + getter (mirrors dummyFluidItem()'s pattern)
this.wrappedGenericStack = registry.item("wrapped_generic_stack", WrappedGenericStack::new)
        .creativeTab(null).build();
public IItemDefinition wrappedGenericStack();

// appeng.core.Registration.initialize() — the wave-2 TODO at line ~222 is now:
definitions.items().wrappedGenericStack().maybeItem()
        .ifPresent(item -> GenericStack.setWrapper((GenericStack.Wrapper) item));
InitStackWorldBehaviors.register();          // appeng.parts.automation — agent 3-1's entry point
InitExternalStorageStrategies.register();    // appeng.parts.misc — agent 3-2's entry point
// All three calls sit after StorageCells.addCellHandler/addCellGuiHandler and after the AEKeyType Forge
// registry is populated (that happens in newRegistry(), RegistryEvent.NewRegistry, long before this
// FMLInitializationEvent handler runs) — satisfying the ordering both the wave-2 TODO and this wave's
// brief called for. Neither appeng.parts.automation.InitStackWorldBehaviors nor
// appeng.parts.misc.InitExternalStorageStrategies exists on disk yet at the time of this edit — expected,
// per the big-bang rule; both are `public static void register()` per the brief that named them.
```

**The four rewritten cell/pattern items — same shape of change in all three cell items:**
`IStorageCell<IAEItemStack>` → `IBasicCellItem`; `getChannel(): IStorageChannel<IAEItemStack>` → `getKeyType(): AEKeyType` returning `AEKeyType.items()`; `isBlackListed(ItemStack, IAEItemStack)` → `isBlackListed(ItemStack, AEKey)` (each now pattern-matches `instanceof AEItemKey`, defaulting to blacklisted for any non-item key, matching the old code's `null`-falls-through-to-`true` default); the `AEApi.instance().registries().cell().getCellInventory(stack, null, channel)` tooltip lookup → `StorageCells.getCellInventory(stack, null)` (no channel argument — a cell no longer needs one to be resolved). `getBytes`/`getBytesPerType`/`getTotalTypes`/`getIdleDrain`/`storableInStorageCell`/`isStorageCell` keep their old numeric bodies unchanged in all three items.

**`ToolMatterCannon`:** the ammo-selection/firing loop (`onItemRightClick`) now reads the cell's `KeyCounter.getFirstEntry()` (key + amount together, replacing `IAEItemStack.getStackSize()` living on the same object the identity did) and fires `MEStorage.extract(ammoKey, 1, MODULATE, ...)` once per shot instead of copying/mutating an `IAEItemStack`. `Upgrades.SPEED` shot-count math, the paintball-vs-matter-ball branch (`penetration <= 0`), and the entity/block damage model in `standardAmmo`/`shootPaintBalls` are untouched — only the ammo lookup/extraction plumbing changed. No `Upgrades.MAGNET`/`Upgrades.QUANTUM_LINK` reference exists in this file (only `Upgrades.SPEED`); §10's "at risk" magnet/quantum-link entries live in `UpgradeInventory`/`ItemMaterial`, outside this wave's file list, so nothing to restore here.

**`ToolColorApplicator`:** the cell-backed dye storage (`consumeColor`, `consumeItem`, `setActiveColor`, `findNextColor`, the color-cycling used by `onWheel`/sneak-right-click) now walks `MEStorage.getAvailableStacks()` (a `KeyCounter`) and extracts via `AEItemKey`/`MEStorage.extract` instead of `IMEInventory<IAEItemStack>.getAvailableItems()`/`extractItems()`. Colour cycling, the "next/previous colour" scroll behaviour, and the cell-backed dye storage mechanic are all preserved.

**§9.1 hazard, specifically checked in `ToolColorApplicator.findNextColor`:** the old code cycled a `LinkedList<IAEItemStack>` until `where.equals(anchor)`, where `anchor` is a plain `ItemStack` — this relied on old `AEItemStack.equals(ItemStack)` overriding `Object.equals` to mean "same item, ignore count" (confirmed by reading the pre-migration `AEItemStack.equals` at commit `128816d22`). Translated to `AEItemKey where; ItemStack anchor; where.matches(anchor)` — `AEItemKey.matches(ItemStack)` is the size-insensitive "same item" comparison, i.e. the correct target, **not** `GenericStack.equals()`. No whole-`GenericStack` comparison exists anywhere in the four rewritten files.

**`ItemEncodedPattern`:** `ICraftingPatternDetails.getCondensedInputs()`/`getCondensedOutputs()` now return `GenericStack[]` instead of `IAEItemStack[]`; the tooltip loop reads `anOut.amount()` + `Platform.getItemDisplayName(anOut.what())` in place of `anOut.getStackSize()` + `Platform.getItemDisplayName(anOut)`. `getOutput(ItemStack)` (the cached-icon accessor) now builds its `ItemStack` via `GenericStack.wrapInItemStack(details.getOutputs()[0])` instead of the deleted `IAEItemStack.createItemStack()`; because `AEItemKey` is handled inline by `wrapInItemStack` (CONTRACT.md §1.5), this works today even for pattern outputs (always items in this fork) without depending on the wrapper item at all. Pattern inputs/outputs, substitution flags, and the crafting-vs-processing distinction are all unchanged; `InvalidPatternHelper` (the invalid-pattern tooltip branch) was never touched — it already operated on raw NBT/`ItemStack`, not `IAEItemStack`.

**`ToolPortableCell`:** cell-side shape change only (see above); the GUI hookup (`getGuiObject` → `new PortableCellViewer(is, pos.getX())`) is untouched — `PortableCellViewer`'s constructor was already ported to the same `(ItemStack, int)` signature by the wave-3c agent (`appeng.items.contents`, done in parallel), confirmed by reading the file after that wave landed. `ContainerMEMonitorable`/the portable-cell push design are wave 4's explicitly-deferred decision (CONTRACT.md §10, "Third case") and were not touched here, per the brief.

**Nothing left against `GenericStack.Wrapper` for later waves to resolve** — installed and wired up, closing the gap wave 3c's entry flagged ("whichever wave creates it must also flip the `Registration.java` TODO"). `appeng.util.Platform`, `appeng.core.api.ApiClientHelper` (`keyOf`), `appeng.items.storage.ItemViewCell`/`ItemCreativeStorageCell` (wave 3c) all called `GenericStack.isWrapped`/`unwrapItemStack`/`wrapForDisplayOrFilter` defensively before this wave landed the actual implementation; they now resolve non-item keys instead of throwing `IllegalStateException`.

**Could not be verified in-game** (no compiler feedback per the big-bang rule, and this wave does not include the multi-type filter GUI that would actually display a wrapped stack in a slot): the wrapper item has no client-side model/rendering registered. This is deliberate, not an oversight — CONTRACT.md §5 defers "the multi-type filter GUI" to wave 4 ("no 1.12.2 precedent exists in any source"), and the established precedent for a placeholder item in this codebase (`FluidDummyItem`) is rendered by GUI code drawing the wrapped content's own icon directly rather than through the vanilla item model (see `FluidDummyItemRendering`). Wave 4 should follow the same pattern for `WrappedGenericStack` once the multi-type GUI exists, rather than giving the item its own texture.

Audited clean as of wave 3d: `appeng.items.misc`, `appeng.items.tools.powered` (the four rewritten files) — one identity comparison found and fixed (`ToolColorApplicator.findNextColor`, see above); no whole-`GenericStack` comparisons anywhere in this wave's files.

### Wave 4 prerequisites — done by hand before the wave, do not redo

Two things wave 4 needed in more than one agent's files at once. They are already in the tree; treat them as given.

**1. `appeng.container.me.GridInventoryEntry` (new class).** The old `IAEItemStack` carried three numbers per key — `stackSize`, `countRequestable` and the `isCraftable` flag — and `PacketMEInventoryUpdate` shipped all three to the client. A `GenericStack` carries one `long` and keys carry no craftable flag (§8.3), so the terminal protocol needs a carrier type. It is a port of upstream's `appeng.menu.me.common.GridInventoryEntry` minus the `serial` field, because AE2UD's protocol identifies a row by the key itself rather than by serial and always sends the key:

```java
public class GridInventoryEntry {
    public GridInventoryEntry(@Nonnull AEKey what, long storedAmount, long requestableAmount, boolean craftable);
    @Nonnull AEKey getWhat();          // replaces the old stack identity
    long getStoredAmount();            // replaces IAEItemStack.getStackSize()
    long getRequestableAmount();       // replaces IAEItemStack.getCountRequestable()
    boolean isCraftable();             // replaces IAEItemStack.isCraftable()
    boolean isMeaningful();            // stored > 0 || requestable > 0 || craftable — false means "remove this row"
    GridInventoryEntry withStoredAmount(long newStoredAmount);
    void writeToPacket(ByteBuf) throws IOException;
    static GridInventoryEntry fromPacket(ByteBuf) throws IOException;
}
```

Like the `IAEItemStack` it replaces, it doubles as the generic payload of `PacketMEInventoryUpdate`, and the other containers that packet serves keep reading the two amount fields with their own meaning: `ContainerNetworkStatus` puts a machine count in `storedAmount` and that machine's idle power drain (x100) in `requestableAmount`; `ContainerCraftConfirm` puts the used/missing amount in `storedAmount` and the to-be-crafted amount in `requestableAmount`. That reuse is inherited from the old code, not new.

Consequently `PacketMEInventoryUpdate`'s shape is fixed as: `List<GridInventoryEntry> list` on the receiving side, `appendItem(GridInventoryEntry)` on the sending side, the `byte ref` constructor argument unchanged, and the gzip framing/`OPERATION_BYTE_LIMIT`/`BufferOverflowException` behaviour unchanged. `postUpdate` on the four receiving containers takes `List<GridInventoryEntry>` (plus the `byte ref` where it already did).

**2. `appeng.util.Platform.extractItemsByRecipe`'s last parameter is now `AEKeyFilter`**, not `IPartitionList` (`filter == null || filter.matches(key)`). Its two call sites — `SlotCraftingTerm` and `ContainerPatternEncoder` — pass `ItemViewCell.createFilter(...)` straight in. Nothing else to adapt. (An earlier note in `ItemViewCell`'s javadoc listed `PacketJEIRecipe` as a third call site; it is not one — it has its own fill algorithm and never called this method.)

**3. Signatures fixed up front because more than one wave-4 agent meets at them.** These are decided; implement them exactly, and call them exactly. Not a suggestion, and not renegotiable mid-wave — if one looks wrong, stop and report rather than choosing your own.

```java
// appeng.container.AEBaseContainer                       (agent 4-3 implements)
@Nullable AEKey getTargetStack();                         // was IAEItemStack; only identity + display were ever read
void setTargetStack(@Nullable AEKey stack);
MEStorage getCellInventory();                             // was IMEInventoryHandler<IAEItemStack>
void setCellInventory(MEStorage cellInv);

// appeng.core.sync.packets.PacketInventoryAction         (agent 4-1 implements)
PacketInventoryAction(InventoryAction action, int slot, @Nullable GenericStack slotItem);
PacketInventoryAction(InventoryAction action, IJEITargetSlot slot, @Nullable GenericStack slotItem);

// appeng.core.sync.packets                               (agent 4-1 implements)
PacketTargetItemStack(@Nullable AEKey what);              // both still exist and still dispatch to the
PacketTargetFluidStack(@Nullable AEKey what);             // same containers as before; do not merge them
PacketPatternSlot(IItemHandler pat, @Nullable GenericStack slotItem, boolean shift);   // GenericStack[9] pattern
PacketAssemblerAnimation(BlockPos pos, byte rate, GenericStack is);                    // pinned by TileMolecularAssembler (wave 2)
PacketCraftingToast(GenericStack stack, boolean cancelled);                            // pinned by CraftingCPUCluster (wave 2)
PacketInformPlayer(GenericStack expected, @Nullable GenericStack actual, InfoType type); // pinned by CraftingTreeNode/MECraftingInventory (wave 2)

// appeng.container.implementations.ContainerCraftAmount  (agent 4-2 implements)
@Nullable AEKey getItemToCraft();
void setItemToCraft(@Nonnull AEKey itemToCreate);
```

The old `PLACE_JEI_GHOST_ITEM` path in `PacketInventoryAction` detected a fluid by unwrapping `slotItem.getDefinition().getTagCompound()` through `AEFluidStack.fromNBT` — i.e. the fluid was smuggled inside a dummy item. With a `GenericStack` payload the fluid arrives as an `AEFluidKey` directly; test `slotItem.what() instanceof AEFluidKey` instead. The ghost-item-into-fluid-slot mechanic must survive that change, including the 1000 mB default it applied.

### Wave 4a — appeng.core.sync.packets (done)

Eleven files: `PacketMEInventoryUpdate`, `PacketMEFluidInventoryUpdate`, `PacketInventoryAction`, `PacketPatternSlot`, `PacketJEIRecipe`, `PacketFluidSlot`, `PacketAssemblerAnimation`, `PacketCraftingToast`, `PacketInformPlayer`, `PacketTargetItemStack`, `PacketTargetFluidStack`. None of these files had been touched since before the migration started (`git diff 1e855f729` was empty for the whole package going in), so every one of them was still on the fully old model — there was no partial-migration state to reconcile, only a straight port.

```java
// appeng.core.sync.packets.PacketMEInventoryUpdate — the shape fixed by the §9 prerequisites, verbatim
private final List<GridInventoryEntry> list;              // was List<IAEItemStack>
public void appendItem(GridInventoryEntry is);             // was appendItem(IAEItemStack)
// byte ref constructor arg, gzip framing, OPERATION_BYTE_LIMIT/UNCOMPRESSED_PACKET_BYTE_LIMIT,
// BufferOverflowException re-chunking: untouched. clientPacketData's four dispatch targets
// (ContainerCraftConfirm/ContainerCraftingCPU/ContainerMEMonitorable/ContainerNetworkStatus) and the
// byte ref forwarded to the first two: untouched, now typed List<GridInventoryEntry>.

// appeng.core.sync.packets.PacketMEFluidInventoryUpdate — kept, not merged into PacketMEInventoryUpdate
private final List<GridInventoryEntry> list;               // was List<IAEFluidStack>
public void appendFluid(GridInventoryEntry fs);             // was appendFluid(IAEFluidStack)
// Same gzip/limit machinery as PacketMEInventoryUpdate, untouched. Dispatch is unchanged and unusual for
// this packet family: it goes straight to the CLIENT SCREEN, not the container —
// clientPacketData reads Minecraft.getMinecraft().currentScreen and calls postUpdate(List<GridInventoryEntry>)
// on it if it is a GuiFluidTerminal or GuiWirelessFluidTerminal (both appeng.fluids.client.gui, wave 5).
// Requirement for wave 5: both classes' postUpdate must become postUpdate(List<GridInventoryEntry>).

// appeng.core.sync.packets.PacketFluidSlot
private final Map<Integer, GenericStack> list;              // was Map<Integer, IAEFluidStack>
public PacketFluidSlot(Map<Integer, GenericStack> list);
// NBT (de)serialisation per entry goes through GenericStack.readTag/writeTag instead of
// AEFluidStack.fromNBT/writeToNBT; an empty slot is still a missing/null map entry, same as before.
// Dispatch unchanged: both clientPacketData and serverPacketData forward to IFluidSyncContainer.
// Requirement for wave 5: IFluidSyncContainer.receiveFluidSlots must become
// receiveFluidSlots(Map<Integer, GenericStack> fluids), and FluidSyncHelper (which builds/reads these
// maps on ContainerFluidConfigurable's side) must be updated to match this map's value type.

// appeng.core.sync.packets.PacketInventoryAction — the two constructors pinned by the §9 prerequisites
public PacketInventoryAction(InventoryAction action, int slot, @Nullable GenericStack slotItem);
public PacketInventoryAction(InventoryAction action, IJEITargetSlot slot, @Nullable GenericStack slotItem);
public PacketInventoryAction(InventoryAction action, int slot, long id);   // unchanged, no stack carried
// AUTO_CRAFT: baseContainer.getTargetStack() (AEKey) -> cca.getCraftingItem().putStack(target.wrapForDisplayOrFilter())
// for display, cca.setItemToCraft(target) for the real craft target — same split as before (display stack
// vs. "the *actual* item that matters"), just AEKey instead of IAEItemStack.
// PLACE_JEI_GHOST_ITEM, all three destinations preserved:
//  - ContainerFluidConfigurable: fires only when slotItem.what() instanceof AEFluidKey; builds
//    new GenericStack(fluidKey, AEFluidKey.AMOUNT_BUCKET) (the 1000 mB default, unconditional, exactly
//    like the old aefs.setStackSize(1000)) and calls getFluidConfigInventory().setFluidInSlot(slot, ...).
//    Requirement for wave 5: IAEFluidTank.setFluidInSlot must accept a GenericStack (or the equivalent)
//    in its second parameter for this call to resolve.
//  - ContainerInterfaceConfigurationTerminal: unchanged ConfigTracker/getSlotByID/WrapperRangeItemHandler
//    plumbing; the placed stack is now GenericStack.wrapInItemStack(slotItem) instead of
//    slotItem.createItemStack() (incidentally null-safe now, where the old call would NPE on a null stack).
//  - plain SlotFake: senderSlot.putStack(GenericStack.wrapInItemStack(slotItem)); the old fallback that
//    re-derived a fluid stack from NBT when the first putStack left the slot empty is kept in shape
//    (guarded by slotItem.what() instanceof AEFluidKey) but is now provably unreachable — wrapInItemStack
//    cannot return an empty stack for a non-null GenericStack, unlike the old createItemStack() path it
//    replaces. Kept rather than deleted per rule 6; flagged here rather than silently dropped.
// UPDATE_HAND echo: unchanged, now built from a (GenericStack) null / GenericStack.wrapInItemStack(slotItem).

// appeng.core.sync.packets.PacketPatternSlot
@Nullable public final GenericStack slotItem;               // was IAEItemStack
public final GenericStack[] pattern = new GenericStack[9];  // was IAEItemStack[9]
public PacketPatternSlot(IItemHandler pat, @Nullable GenericStack slotItem, boolean shift);
// pattern[] is built from pat's slots via GenericStack.fromItemStack(pat.getStackInSlot(x)), replacing
// AEApi.instance().storage().getStorageChannel(IItemStorageChannel.class).createStack(...). Dispatch
// (ContainerPatternEncoder.craftOrGetItem(this)) unchanged.

// appeng.core.sync.packets.PacketJEIRecipe
// NOT a call site of Platform.extractItemsByRecipe, despite the §9 prerequisite note listing it as a
// third one alongside SlotCraftingTerm/ContainerPatternEncoder — verified by reading the file (untouched
// since before the migration, confirmed via `git diff 1e855f729`) and by grepping the whole tree for
// extractItemsByRecipe( call sites: only SlotCraftingTerm and ContainerPatternEncoder call it, now and
// before this port. PacketJEIRecipe has always had its own hand-rolled per-slot fill algorithm (it fills
// a crafting grid from an arbitrary HEI/JEI ingredient list, not from a matched vanilla IRecipe, so
// extractItemsByRecipe's "re-run the recipe to verify a substitute" trick does not apply). What *is*
// preserved from the prerequisite note is the actual intent: ItemViewCell.createFilter(cct.getViewCells())
// is fed straight into this file's own filter.matches(request) check, same role, same call. Reported here
// per rule 2/7 rather than silently forcing a call that was never there.
// Per-slot algorithm unchanged: exact-match check -> put mismatched item away (now via
// Platform.poweredInsert(..., AEKey, long, ...), which returns the amount actually inserted rather than a
// leftover stack, so "leftover = requested - inserted; EMPTY unless useRealItems() and leftover > 0"
// replaces the old "out == null means fully absorbed" check) -> extract by identity
// (Platform.poweredExtraction, same long-returns-amount translation) -> fuzzy-damage fallback (now
// IStorageService.getCachedInventory().findFuzzy(request, FuzzyMode.IGNORE_ALL), replacing
// IMEMonitor.getStorageList().findFuzzy(...) — this is exactly the fuzzy-search entry point the §4.3/§9
// pointer described; no api gap) -> player-inventory fallback (AdaptorItemHandler, untouched, never part
// of the storage model) -> preview-only placeholder. crafting.getCraftingFor(AEKey, ...) unchanged shape.
// grid.getCache(IStorageGrid.class) -> grid.getCache(IStorageService.class); inv.getInventory(channel) ->
// inv.getInventory() (no channel argument, MEStorage is not per-channel).

// appeng.core.sync.packets.PacketAssemblerAnimation — pinned by TileMolecularAssembler (wave 2)
public PacketAssemblerAnimation(BlockPos pos, byte rate, GenericStack is);
// is field now GenericStack, (de)serialised via GenericStack.readBuffer/writeBuffer. Client-side consumer
// (AELog/ClientHelper -> AssemblerFX, both appeng.client, wave 4-4) reads the public `is`/`rate` fields;
// AssemblerFX's constructor parameter must become GenericStack to match.

// appeng.core.sync.packets.PacketCraftingToast — pinned by CraftingCPUCluster (wave 2)
public PacketCraftingToast(GenericStack stack, boolean cancelled);
// stack (de)serialised via GenericStack.readBuffer/writeBuffer. doCraftingToast() now builds the toast's
// ItemStack via GenericStack.wrapInItemStack(stack) instead of stack.asItemStackRepresentation() —
// CraftingStatusToast's own (ItemStack, boolean) constructor (appeng.client.gui.toasts, untouched) is
// unaffected.

// appeng.core.sync.packets.PacketInformPlayer — pinned by CraftingTreeNode/MECraftingInventory (wave 2)
public PacketInformPlayer(GenericStack expected, @Nullable GenericStack actual, InfoType type);
// reportedItem/actualItem now GenericStack, (de)serialised via GenericStack.readBuffer/writeBuffer. The
// two chat messages read reportedItem.amount()/reportedItem.what().getDisplayName().getFormattedText()
// (the established pattern already used by Platform.java/ApiClientHelper/WrappedGenericStack) and
// GenericStack.getStackSizeOrZero(actualItem) in place of the old getStackSize()/getItem().
// getItemStackDisplayName(getDefinition()) pair. InfoType.PARTIAL_ITEM_EXTRACTION/NO_ITEMS_EXTRACTED
// unchanged.

// appeng.core.sync.packets.PacketTargetItemStack / PacketTargetFluidStack — both kept, not merged
public PacketTargetItemStack(@Nullable AEKey what);
public PacketTargetFluidStack(@Nullable AEKey what);
// Both fields are now a bare AEKey, (de)serialised via AEKey.writeOptionalKey/readOptionalKey (which
// already writes/reads the presence boolean, so the old stream.readableBytes()>0 probe on the read side
// is no longer needed — both sides always write/read the boolean). Dispatch unchanged:
// PacketTargetItemStack -> AEBaseContainer.setTargetStack(AEKey) (wave 4-3); PacketTargetFluidStack ->
// ContainerFluidTerminal/ContainerWirelessFluidTerminal/ContainerFluidInterface (all wave 5,
// appeng.fluids.container — must implement void setTargetStack(AEKey stack)) and
// ContainerFluidInterfaceConfigurationTerminal (wave 4-3, same signature).
```

**§9.1 `.equals(` audit, all eleven files:** grepped every file for `.equals(`. `PacketJEIRecipe.canUseInSlot` uses `ItemStack.areItemStacksEqual(is, option)` (vanilla `ItemStack` comparison, pre-existing, never touched an `AEKey`/`GenericStack`). No other file calls `.equals(` on a stack at all — every identity check in this wave's files compares `AEKey`s directly (`instanceof AEItemKey`/`AEFluidKey`, `AEItemKey.of(...)` identity, `KeyCounter`/`Object2LongMap.Entry<AEKey>` lookups) or compares vanilla `ItemStack`/reference identity that was never `IAEItemStack`-shaped to begin with (`newItem != currentItem` in `PacketJEIRecipe`, `senderSlot.getStack().isEmpty()` in `PacketInventoryAction`). No whole-`GenericStack` comparison exists anywhere in this wave's files. Clean across all eleven.

**API gaps hit: none.** Every method this wave needed — `AEKey.writeKey`/`readKey`/`writeOptionalKey`/`readOptionalKey`, `GenericStack.readBuffer`/`writeBuffer`/`wrapInItemStack`/`fromItemStack`, `Platform.poweredInsert`/`poweredExtraction`/`extractItemsByRecipe`/`isGTDamageableItem`, `IStorageService.getInventory`/`getCachedInventory`, `KeyCounter.findFuzzy`, `AEKey.wrapForDisplayOrFilter`, `ICraftingGrid.getCraftingFor` — was already in `src/api` or `appeng.util.Platform` from earlier waves, exactly as specified. `src/api` was not touched.

**Mechanics named in the brief, verified preserved:** the `AUTO_CRAFT` GUI hop and target-stack forwarding (`PacketInventoryAction`); `PLACE_JEI_GHOST_ITEM` into all three destinations, 1000 mB default included (`PacketInventoryAction`); the `UPDATE_HAND` echo (`PacketInventoryAction`); the pattern-terminal craft-from-pattern shift-click carrying a 9-slot pattern (`PacketPatternSlot`); the molecular assembler's client animation and `rate` byte (`PacketAssemblerAnimation`); the crafting-completion/cancellation toast (`PacketCraftingToast`); the "could not extract what it needed" player notification with both `InfoType` variants (`PacketInformPlayer`); recipe transfer into both the crafting terminal and the expanded processing pattern terminal, the `useRealItems()` distinction, the fuzzy fallback, and view-cell filtering (`PacketJEIRecipe`); the fluid terminal's live update channel, kept as a distinct class (`PacketMEFluidInventoryUpdate`); both target-stack packets kept distinct (`PacketTargetItemStack`/`PacketTargetFluidStack`).

**Changed outside the assigned file list: none.** Every edit in this wave lives inside the eleven files above; nothing in `container/implementations`, `client/*`, `fluids/*` or `src/api` was touched. Where this wave's files call into another wave's classes (`GridInventoryEntry` — already existed, used as-is per the "do not modify it" instruction; `ContainerFluidConfigurable`, `IFluidSyncContainer`, `GuiFluidTerminal`/`GuiWirelessFluidTerminal`, `ContainerFluidTerminal`/`ContainerWirelessFluidTerminal`/`ContainerFluidInterface` — all wave 5; `AEBaseContainer`, `ContainerCraftAmount`, `ContainerInterfaceConfigurationTerminal`, `ContainerFluidInterfaceConfigurationTerminal` — waves 4-2/4-3), the call sites were written against the pinned/expected signatures and left for those agents/waves to satisfy, exactly as CONTRACT.md's cross-agent process describes.

**Could not be verified in-game / left for later waves:** `GuiFluidTerminal`/`GuiWirelessFluidTerminal`.postUpdate, `IFluidSyncContainer.receiveFluidSlots`, `FluidSyncHelper`, `IAEFluidTank.setFluidInSlot`, and the three `ContainerFluid*` classes' `setTargetStack` — all wave 5, all listed above with the exact signature this wave's packets now call. `AssemblerFX`'s constructor parameter (wave 4-4, `appeng.client.render.effects`) must become `GenericStack` to match `PacketAssemblerAnimation.is`; `CraftingStatusToast` needed no change (already `(ItemStack, boolean)`, fed via `GenericStack.wrapInItemStack`).

### Wave 4b — appeng.container.implementations, crafting side (done)

Seven files: `ContainerCraftAmount`, `ContainerCraftConfirm`, `ContainerCraftingCPU`, `CraftingCPUStatus`, `ContainerPatternEncoder`, `ContainerWirelessPatternTerminal`, `ContainerNetworkStatus`.

```java
// appeng.container.implementations.ContainerCraftAmount — matches the §9 prerequisite exactly
@Nullable AEKey getItemToCraft();
void setItemToCraft(@Nonnull AEKey itemToCreate);
// The old code's split between the display ItemStack (the SlotInaccessible `craftingItem`) and "the
// *actual* item that matters" (`itemToCreate`, now an AEKey) is untouched — this class never built the
// display stack itself (PacketInventoryAction, agent 4-1's file, does that via
// AEKey.wrapForDisplayOrFilter()/GenericStack.wrapInItemStack), it only ever held the two fields apart.

// appeng.container.implementations.CraftingCPUStatus — no public shape change except the field type
GenericStack getCrafting();   // was IAEItemStack; ICraftingCPU.getFinalOutput() already returned GenericStack
// NBT (de)serialisation of `crafting` goes through GenericStack.readTag/writeTag instead of
// AEItemStack.fromNBT/writeToNBT. writeToPacket/the ByteBuf constructor are untouched (they still go
// through an embedded NBTTagCompound via CompressedStreamTools, unaffected by the field's type).
// NOT touched: appeng.container.implementations.ContainerCraftingStatus and CraftingCPURecord, both of
// which reference this class but never call getCrafting() themselves (only the client-side
// GuiCraftingStatus does) and reference no other deleted type — confirmed clean, no edit needed.

// appeng.container.implementations.ContainerCraftingCPU implements ICraftingCPUListener (was
//         IMEMonitorHandlerReceiver<IAEItemStack>), ICustomNameObject
// Push, not polling (CONTRACT.md rule 6 / §10): setCPU() registers via
// CraftingCPUCluster#addListener(this, null) and detach paths (removeListener(IContainerListener),
// onContainerClosed, CPU swap) all call CraftingCPUCluster#removeListener(this), exactly mirroring the old
// IBaseMonitor#addListener/#removeListener pair. onCraftingCPUChange(AEKey, IActionSource) only adds the
// key to a tracked-keys set (a plain LinkedHashSet<AEKey>, replacing the old IItemList<IAEItemStack> that
// postChange fed with size-1 markers — only identity ever mattered there, never the number); the seed set
// on CPU attach comes from CraftingCPUCluster#getListOfItem(KeyCounter, CraftingItemList.ALL). Every server
// tick, for every tracked key, detectAndSendChanges() re-reads the authoritative current amounts via
// CraftingCPUCluster#getItemStack(AEKey, CraftingItemList) for STORAGE/ACTIVE/PENDING and ships them as
// three GridInventoryEntry-based PacketMEInventoryUpdate packets (ref 0/1/2), same as before. getMonitor(),
// getNetwork(), setCPU(ICraftingCPU) keep their exact old signatures — appeng.container.implementations.
// ContainerCraftingStatus (not in this wave's file list) extends this class and calls all three; confirmed
// unaffected since it references no other deleted type either.
public void postUpdate(List<GridInventoryEntry> list, byte ref);   // was List<IAEItemStack>

// appeng.container.implementations.ContainerNetworkStatus
// Reuses the ME-update packet for machines, not items (CONTRACT.md §10) - preserved exactly: for each
// machine class, a machine's representation ItemStack (as an AEItemKey) accumulates a count into one
// KeyCounter and that machine's idle power drain x100 into a second KeyCounter, mirroring the old
// IItemList.add() merge-by-identity behaviour (which summed both stackSize and countRequestable for
// matching stacks). Sent as GridInventoryEntry(what, count, power, false) — machine count in
// storedAmount, idle-power-x100 in requestableAmount, exactly as the prerequisite note describes.
public void postUpdate(List<GridInventoryEntry> list);   // was List<IAEItemStack>; no ref byte, unchanged

// appeng.container.implementations.ContainerCraftConfirm — §9.2 applies, see below
// appeng.container.implementations.ContainerPatternEncoder / ContainerWirelessPatternTerminal — see below
```

**§9.2 resolved: held the concrete `CraftingJob` type — worked without friction.** `ContainerCraftConfirm.result` is now declared as `appeng.crafting.CraftingJob` (public class, public two-argument `populatePlan(KeyCounter used, KeyCounter requestable)`) instead of the frozen `ICraftingJob`. The only place that needed a cast is where the field is populated — `this.result = (CraftingJob) this.getJob().get();` — since `Future<ICraftingJob>` is what the frozen `ICraftingGrid.beginCraftingJob`/the `job` field must stay typed as. Every other use of `this.result` (`isSimulation()`, `getByteTotal()`, `getOutput()`, passing it to `ICraftingGrid.submitJob(ICraftingJob, ...)`) already upcasts implicitly, so nothing else needed touching. No amendment to `ICraftingJob` was needed; the alternative (amending the frozen interface) was not required.

The used/missing vs to-craft split itself: `populatePlan(used, requestable)` fills two `KeyCounter`s (mirroring `CraftingTreeNode.getPlan`, which adds a key to only one of the two counters depending on whether it came from storage or from crafting-emission, never necessarily both) — so the loop iterates the **union** of `used.keySet()` and `requestable.keySet()`, not just one of them, otherwise a key present in only one counter would silently vanish from that column. For each key: `a` (ref 0) carries the used/available amount (after a `MEStorage.extract(..., Actionable.SIMULATE, ...)` probe when `result.isSimulation()`, mirroring the old `IMEMonitor.extractItems(..., SIMULATE, ...)` call exactly — `extract()`'s `long` return already *is* "the amount actually available", so the old null-check/copy dance collapses to a subtraction), `b` (ref 1) carries the to-craft amount unconditionally, `c` (ref 2, simulation-only) carries the missing amount (requested-minus-available) — the same three-packet split, the same conditions for sending each, as the pre-migration code.

**`ContainerPatternEncoder`/`ContainerWirelessPatternTerminal` fork mechanics, checked and preserved:**

- **Expanded processing pattern terminal / substitution / crafting-vs-processing mode.** None of `craftingMode`, `substitute`, `isCraftingMode()`/`setCraftingMode()`, `isSubstitute()`/`setSubstitute()`, or the output-slot-ordering swap between the two modes touch a storage type at all (they read/write NBT flags and vanilla `ItemStack`s exclusively) — untouched, confirmed by reading the whole file; only `craftOrGetItem` and the pattern-detail-to-slot copy in `ContainerWirelessPatternTerminal.onChangeInventory` needed changes.
- **`craftOrGetItem(PacketPatternSlot)`** — rewritten around `GenericStack`/`AEKey`/`MEStorage` per the §9 prerequisites (`PacketPatternSlot`'s `slotItem` is now a `GenericStack`, `Platform.poweredExtraction` takes `(IEnergySource, MEStorage, AEKey, long, IActionSource)` and returns the extracted `long` instead of a leftover stack, `Platform.extractItemsByRecipe`'s last parameter is `AEKeyFilter` fed directly from `ItemViewCell.createFilter(...)`, and `getCellInventory().injectItems(...)` became `getCellInventory().insert(AEKey, long, Actionable, IActionSource)`). The 3x3 crafting-preview grid is inherently vanilla-`ItemStack`-only, so `slotItem.what()` is required to be an `AEItemKey` (bails out otherwise) — this is not a new restriction, the pre-migration code made the same assumption implicitly by always treating the payload as an item.
- **`getPart().getInventory()` / `((ITerminalHost) iGuiItemObject).getInventory()`** — both now the no-arg `MEStorage`-returning form (`ITerminalHost`/`AbstractPartTerminal`, wave 2/3b), replacing the old per-channel `getInventory(IStorageChannel<T>)` lookup.
- **`ICraftingPatternDetails.getInputs()`/`getOutputs()` → `GenericStack[]`.** `ContainerWirelessPatternTerminal.onChangeInventory`'s copy from a scanned pattern into the crafting/output preview slots now uses `GenericStack.wrapInItemStack(item)` in place of `item == null ? ItemStack.EMPTY : item.createItemStack()` — `wrapInItemStack` already returns `ItemStack.EMPTY` for a `null` argument, so the null check collapsed into the one call.
- **View-cell filtering (`ItemViewCell.createFilter`) feeding `Platform.extractItemsByRecipe`.** Both of this wave's remaining call sites (`ContainerPatternEncoder.craftOrGetItem`; the third, `SlotCraftingTerm`, belongs to agent 4-3) pass `ItemViewCell.createFilter(this.getViewCells())` straight into the now-`AEKeyFilter`-typed last parameter, per the §9 prerequisite — no adapter needed, `Platform.extractItemsByRecipe` in `appeng.util` already has the new signature (confirmed by reading it; it was updated ahead of the wave along with the other prerequisites).

**§9.1 `.equals(` audit, all seven files:** grepped every file for `.equals(` — the only hits are `String.equals(...)` (`ContainerPatternEncoder.getInventoryByName`/`onChangeInventory`'s field-name checks, `ContainerWirelessPatternTerminal.getInventoryByName`'s name checks) and vanilla-`ItemStack` comparisons via `Platform.itemComparisons().isSameItem(...)`/`ItemStack.areItemsEqual(...)` (both files, unrelated to `AEKey`/`GenericStack`). No file compares a whole `GenericStack` for identity and no file carries over an old `IAEItemStack.equals(...)`-style comparison; every place that used to compare stack identity now compares `AEKey`s directly (`KeyCounter.get(AEKey)`/`.keySet()` lookups keyed by `AEItemKey`/`AEKey` identity, `outKey instanceof AEItemKey`) or vanilla `ItemStack`s where the comparison always was about the physical item, not an `AEKey`. Clean across all seven files.

**API gaps hit: none.** No method was missing from `src/api`; every signature named in the §9 "Wave 4 prerequisites" section (`GridInventoryEntry`, `Platform.extractItemsByRecipe`'s `AEKeyFilter` parameter, `AEBaseContainer.getTargetStack()`/`getCellInventory()`, `ContainerCraftAmount.getItemToCraft()`/`setItemToCraft()`) was already in place or implemented exactly as specified.

**Mechanics that could not be verified in-game:** none outstanding beyond the standing cross-agent debt already on record — `PacketMEInventoryUpdate.appendItem(GridInventoryEntry)` (agent 4-1), the client-side `GuiCraftingCPU`/`GuiCraftConfirm`/`GuiNetworkStatus`/`GuiCraftingStatus` screens that read the packets this wave now sends (agent 4-4, all of which still reference `IAEItemStack`/`IItemList` as of this writing and must be updated to `GridInventoryEntry`/`GenericStack` to compile) — none of this wave's files depend on those changes to be internally consistent, only on the rest of the wave landing for the whole package to compile.

**Changed outside the assigned file list: none.** `appeng.container.implementations.ContainerCraftingStatus` and `CraftingCPURecord` were read (they touch `ContainerCraftingCPU`/`CraftingCPUStatus`, both mine) and confirmed to reference no deleted type and call no method whose signature this wave changed — left untouched.

### Wave 4c — appeng.container, storage side (done)

Eight files: `AEBaseContainer`, `ContainerMEMonitorable`, `ContainerStorageBus`, `ContainerOreDictStorageBus`, `ContainerCellWorkbench`, `ContainerFluidInterfaceConfigurationTerminal`, `container/slot/SlotCraftingTerm`, `container/slot/SlotPatternTerm`. One file touched outside the assigned list: `appeng.parts.reporting.AbstractPartTerminal` (see "Third case, case 1" below — a Rule 6 call, not an accident).

```java
// appeng.container.AEBaseContainer — the accessors pinned by the §9 prerequisites, implemented verbatim
@Nullable AEKey getTargetStack();
void setTargetStack(@Nullable AEKey stack);       // sends PacketTargetItemStack(AEKey); equals() is safe
                                                    // here because AEKey (unlike GenericStack) never carries
                                                    // an amount - see the §9.1 audit below
MEStorage getCellInventory();
void setCellInventory(MEStorage cellInv);
// The ME-slot InventoryAction switch (SHIFT_CLICK, ROLL_DOWN, ROLL_UP/PICKUP_SINGLE, PICKUP_OR_SET_DOWN,
// SPLIT_OR_PLACE_SINGLE, CREATIVE_DUPLICATE, MOVE_REGION, ~200 lines) is rewritten around AEKey/AEItemKey
// and Platform.poweredInsert/poweredExtraction's new `long` (amount actually moved) return value in place
// of the old "returns the leftover IAEItemStack" contract. The whole switch guards on
// `slotItem instanceof AEItemKey` and no-ops otherwise - this is not a new restriction, every branch was
// already item-only before this port (it moves stacks into/out of the player's vanilla inventory, which
// cannot hold anything else); mirrors upstream MEStorageMenu#handleNetworkInteraction's identical
// `if (!(clickedKey instanceof AEItemKey clickedItem)) return;` guard for the same action set. Every
// branch's arithmetic was traced against the pre-migration file line by line before rewriting (per rule 7):
// SHIFT_CLICK/MOVE_REGION's "how much fits in the player's inventory, then extract exactly that much"
// two-step; ROLL_DOWN's insert-then-decrement-cursor-then-roll-back-on-failure sequence (the old `fail`
// variable holds what *was* removed, not a failure - `fail.isEmpty()` means the cursor decrement itself
// failed, triggering the rollback extract, not the common case); ROLL_UP/PICKUP_SINGLE's lift-eligibility
// check and its own "couldn't fit on cursor, put it back" rollback; PICKUP_OR_SET_DOWN's
// extract-full-stack-or-insert-full-stack pair; SPLIT_OR_PLACE_SINGLE's SIMULATE-then-halve-then-charge
// sequence. All preserved with identical control flow, only the stack-vs-long return type translated.
// updateHeld/transferStackToContainer/shiftStoreItem: PacketInventoryAction(UPDATE_HAND, 0, GenericStack)
// (was AEItemStack.fromItemStack); shiftStoreItem returns the AEItemKey leftover as `what.toStack(remaining)`
// instead of the old leftover-stack contract, same shape.

// appeng.container.implementations.ContainerMEMonitorable implements IStorageWatcherNode (was
//         IMEMonitorHandlerReceiver<IAEItemStack>)
// Both live-update cases from CONTRACT.md §10 "Third case", see the dedicated write-up below. Constructor
// structure (IPortableCell / IMEChest / IGridHost-or-IActionHost branching, power source wiring, view-cell
// slots, jeiOffset) is untouched. `monitor` is now a bare MEStorage (was IMEMonitor<IAEItemStack>); the old
// per-container `IItemList<IAEItemStack> items` field is gone (nothing outside this file ever read it,
// confirmed by grep) and its bookkeeping role is split across `pendingPushChanges` (case 1),
// `previousAvailableStacks` (case 2) and `previousCraftables` (both cases, §8.3).
public void postUpdate(List<GridInventoryEntry> list);   // was List<IAEItemStack>; forwards to GuiMEMonitorable
// onListUpdate/postChange/isValid (IMEMonitorHandlerReceiver's methods) are gone - there is no interface
// left to implement them for. The craftable flag (§8.3) is computed fresh every tick from
// ICraftingGrid.getCraftables(AEKeyFilter.all()) and diffed against the previous tick's set, independently
// of which live-update case is active, because neither model has a craftable watcher.

// appeng.container.implementations.ContainerStorageBus / ContainerOreDictStorageBus
// partition()/ore-scan rewritten around PartStorageBus/PartOreDicStorageBus#getInternalHandler()'s new
// return type (a plain appeng.me.storage.MEInventoryHandler, a MEStorage): getAvailableStacks().keySet()
// walks the keys, AEKey.wrapForDisplayOrFilter() replaces the old asItemStackRepresentation() for the
// config slots. The ore-dictionary scan replaces the deleted ((AEItemStack) x).getOre() cast with
// appeng.util.item.OreHelper.INSTANCE.getOre(itemKey.getReadOnlyStack()), filtering to AEItemKey first
// (ore references are inherently item-only). Settings.STICKY_MODE/STORAGE_FILTER/ACCESS,
// FuzzyMode/Upgrades.CAPACITY, and the upgrade slots are untouched GuiSync'd fields/enums, never part of
// the storage model.

// appeng.container.implementations.ContainerCellWorkbench
// partition() rewritten: AEApi.instance().registries().cell().getCellInventory(is, null, channel) (which
// needed an IStorageChannel derived from IStorageCell.getChannel()) collapses to
// appeng.api.storage.StorageCells.getCellInventory(is, null) - a cell no longer needs to be told its key
// type up front, so the whole channel-lookup dance is gone. Result iterated the same way as the two bus
// containers above (getAvailableStacks().keySet(), wrapForDisplayOrFilter()). ICellWorkbenchItem moved
// package (api.storage.cells, name unchanged per §4.2) but its setFuzzyMode/getFuzzyMode calls, the
// copy-settings button (nextWorkBenchCopyMode/CopyMode), and the partition-from-cell button are otherwise
// untouched.

// appeng.container.implementations.ContainerFluidInterfaceConfigurationTerminal
// Boundary fix only, per the brief: setTargetStack(IAEFluidStack) -> setTargetStack(AEKey), field
// clientRequestedTargetFluid retyped to AEKey, identity check now stack.equals(...) (AEKey carries no
// amount, so this is already the size-insensitive check the old FluidStack#isFluidEqual was), and the
// PacketTargetFluidStack(AEKey) constructor called directly (no more smuggling a fluid through a dummy
// item's NBT). Settings.INTERFACE_TERMINAL and the FLUID_INTERFACE_CONFIGURATION_TERMINAL part mechanic are
// untouched and still present in the file (regenList/addFluids/FluidConfigTracker/doAction) - they still
// reference appeng.fluids.util.{IAEFluidTank,AEFluidInventory,AEFluidStack}, which are wave 5's `appeng.fluids`
// package and still internally reference the deleted IAEFluidStack today. This file will not fully compile
// until wave 5 lands, exactly as CONTRACT.md's big-bang rule expects - the boundary this wave owns
// (AEBaseContainer-shaped setTargetStack) is the only part fixed here, deliberately.

// appeng.container.slot.SlotCraftingTerm / SlotPatternTerm
// `IStorageMonitorable storage` -> `ITerminalHost storage` (the interface these constructors actually
// receive at every call site - ContainerCraftingTerm/ContainerWirelessCraftingTerminal/ContainerPatternTerm/
// ContainerWirelessPatternTerminal, none of which needed edits since they already passed an ITerminalHost).
// this.storage.getInventory(channel) -> this.storage.getInventory() (MEStorage, no channel argument).
// craftItem/preCraft/postCraft's IMEMonitor<IAEItemStack>/IItemList params became MEStorage/KeyCounter.
// postCraft's "put back what didn't fit onto the crafting grid" step now reads
// inv.insert(AEItemKey, amount, MODULATE, src)'s returned `long inserted` and drops
// `what.toStack(set[x].getCount() - inserted)` when that is positive, replacing the old
// "inject returns the leftover IAEItemStack or null" contract. Platform.extractItemsByRecipe's last
// parameter is now AEKeyFilter (already true of appeng.util.Platform per the §9 prerequisites) and
// ItemViewCell.createFilter(...) (already AEKeyFilter-typed since wave 3c) feeds it directly - no adapter
// needed. SlotPatternTerm.getRequest's PacketPatternSlot payload is now
// GenericStack.fromItemStack(this.getStack()) instead of the old per-channel createStack(...); the
// shift-click packet dispatch itself (PacketPatternSlot(pattern, GenericStack, shift)) is untouched.
```

**"Third case: terminal live updates" (CONTRACT.md §10), both cases implemented:**

1. **Network-backed terminals (plain ME terminal, crafting terminal, pattern terminal, expanded processing
   pattern terminal - anything built on `AbstractPartTerminal`) — real push.** `GridStorageCache.addNode`
   only ever calls `IStorageWatcherNode.updateWatcher` on a node's *machine*, never on whatever container
   happens to have that machine's GUI open right now (a container is not itself a grid node/machine, and
   nothing re-triggers `addNode` when a GUI opens). That means the **part itself**, not the container, has
   to be the one holding the live `IStackWatcher` - so `appeng.parts.reporting.AbstractPartTerminal` (a file
   *outside* this wave's assigned list) was given a small, additive `IStorageWatcherNode` implementation:
   it stores the `IStackWatcher` handed to it by the grid, keeps a list of currently-open terminal
   containers (`addTerminalListener`/`removeTerminalListener`, both new `public` methods, called from
   `ContainerMEMonitorable`'s constructor/`removeListener`/`onContainerClosed`), calls
   `myWatcher.setWatchAll(true)` only while at least one container is attached (so an unopened terminal does
   not force a full-network cache rebuild every tick, per `IStackWatcher.setWatchAll`'s own javadoc -
   "Expensive; used by terminals"), and relays every `onStackChange(AEKey, long)` call to all of them.
   `ContainerMEMonitorable` itself implements `IStorageWatcherNode` too (its `onStackChange` buffers
   `(key, amount)` pairs into `pendingPushChanges`, drained into a `GridInventoryEntry` list every tick in
   `detectAndSendChanges`; its `updateWatcher` is a documented no-op, since the grid never calls it directly).
   This is a **rule 6 case, not an accident**: leaving `AbstractPartTerminal` untouched would have meant
   every terminal built on it had *no* live-update mechanism at all under the new model - not a missed
   optimisation, a full regression of the terminal's core "watch the network live" behaviour. Flagged loudly
   here per the brief's instruction on stepping outside the file list.
2. **Portable cell / view-only cell terminals (`WirelessTerminalGuiObject`, wave 2; `PortableCellViewer`,
   wave 3) and anything else that isn't an `AbstractPartTerminal` (ME chest via `IMEChest`, the security
   station, ...) — server-side per-tick diff.** `ContainerMEMonitorable.collectChanges()` snapshots
   `monitor.getAvailableStacks()`, diffs it against `previousAvailableStacks` (seeded at construction so the
   tick right after a GUI opens does not immediately re-broadcast the same listing `queueInventory` already
   sent), and only ships the delta - exactly upstream's `MEStorageMenu.broadcastChanges()` pattern. The
   craftable-flag diff (`ICraftingGrid.getCraftables(AEKeyFilter.all())` vs. `previousCraftables`) runs
   identically for both cases, since neither model has a craftable watcher.

Case selection is structural, not a guess: `ContainerMEMonitorable`'s constructor already branches on
`instanceof IPortableCell` / `instanceof IMEChest` / `instanceof IGridHost || instanceof IActionHost` (kept
verbatim from the pre-migration file); `networkTerminalPart` is set (enabling case 1) only when
`monitorable instanceof AbstractPartTerminal`, which is true for exactly the parts that fall in the third
branch and false for every portable/chest/security-station host. `TileSecurityStation` (used by
`ContainerSecurityStation`, not mine) *does* reach the `IGridHost`-or-`IActionHost` branch and so gets a
`networkNode`, but is not an `AbstractPartTerminal`, so it uses case 2 - correct, since its `getInventory()`
returns its own small `SecurityStationInventory`, not the whole network, so a per-tick diff of it is cheap.

**Fork-specific mechanics from point 10, verified preserved:**

- **Sticky Card (`Settings.STICKY_MODE`)** — `ContainerStorageBus`/`ContainerOreDictStorageBus`'s
  `@GuiSync(7) public YesNo stickyMode` field and `getStickyMode()`/`setStickyMode()` are untouched; neither
  file's edits touched anything on the settings/GuiSync side, only `partition()`'s cell-scanning internals.
- **Ore-dictionary storage bus** — `ContainerOreDictStorageBus.partition()`'s ore-ID scan, regex-match
  building and `PartOreDicStorageBus#saveOreMatch`/`getOreExp()` plumbing are all intact; only the
  `IAEItemStack`-cast ore lookup was rewritten (see above).
- **`Settings.STORAGE_FILTER`/`Settings.ACCESS`, fuzzy mode, the upgrade slots** — all untouched GuiSync
  fields/config-manager reads in both bus containers.
- **`ICellGuiHandler.isSpecializedFor`** — not called anywhere in `ContainerCellWorkbench` before or after
  this wave (it is a `TileChest.openGui()`/`StorageCells.getGuiHandler(AEKeyType, ItemStack)` concern,
  neither of which is in this file); nothing to preserve or restore here specifically, noted so the next
  reader does not go looking for it in this file.
- **`Settings.INTERFACE_TERMINAL` / the fluid interface configuration terminal** — both still present and
  unedited in `ContainerFluidInterfaceConfigurationTerminal` (see the file's write-up above); only the
  `setTargetStack` boundary was touched.
- **`SlotPatternTerm`'s `PacketPatternSlot` shift-click** — `getRequest(boolean shift)` unchanged in
  behaviour, only the payload's type.
- **`Platform.extractItemsByRecipe`'s view-cell filter / "return leftovers to the network, drop what will
  not fit" path** — `SlotCraftingTerm.craftItem`/`postCraft` preserve both: the recipe re-verification
  against the crafting-terminal's held recipe (`findRecipe`/`handleRecipe`, including the `recipestages`
  compat check), and the "put back what didn't fit, drop what the network also rejects" step in `postCraft`.

**§9.1 `.equals(` audit, all nine files (eight assigned plus `AbstractPartTerminal`):**

- `AEBaseContainer.setTargetStack`: `stack.equals(this.clientRequestedTargetItem)` — safe. This compares two
  bare `AEKey`s, not `GenericStack`s; `AEKey` never carries an amount (only `GenericStack` does), so its
  `equals()` is already the size-insensitive identity check the old `IAEItemStack.isSameType()` was. Not an
  instance of the §9.1 hazard.
- `ContainerFluidInterfaceConfigurationTerminal.setTargetStack`: same reasoning, same conclusion
  (`stack.equals(this.clientRequestedTargetFluid)`, both bare `AEKey`s).
- `ROLL_UP`/`PICKUP_SINGLE` in `AEBaseContainer.doAction`: `slotItemKey.matches(item)` — correctly uses
  `AEItemKey.matches(ItemStack)` (size-insensitive), not `.equals(...)`, translating the old
  `Platform.itemComparisons().isSameItem(slotItem.getDefinition(), item)` call.
- No other file in this wave's list calls `.equals(` on anything stack-shaped. `transferStackInSlot`'s
  existing `Platform.itemComparisons().isSameItem(...)` calls are vanilla-`ItemStack` comparisons that
  predate this port and were never `IAEItemStack`-shaped.
- **No whole-`GenericStack` comparison exists anywhere in this wave's nine files.**

**API gaps hit: none.** Every method needed — `AEKey.wrapForDisplayOrFilter`, `AEItemKey.of`/`.matches`/
`.getReadOnlyStack`/`.toStack`/`.getMaxStackSize`, `GenericStack.fromItemStack`, `MEStorage.insert`/
`.extract`/`.getAvailableStacks`, `KeyCounter.keySet`/`.get`, `StorageCells.getCellInventory`,
`ICraftingGrid.getCraftables`, `IStorageService.getCachedInventory`, `IStackWatcher.setWatchAll` — was
already in `src/api` from earlier waves. `src/api` was not touched.

**Mechanics that could not be preserved: none.** Every branch enumerated in the pre-migration
`AEBaseContainer.doAction` switch, `ContainerMEMonitorable`'s listener/queueInventory/view-cell logic, and
both bus/cell-workbench partition methods has a direct translation in the new model; nothing was dropped
silently or reported as inexpressible.

**Changed outside the assigned file list:** `appeng.parts.reporting.AbstractPartTerminal` — see "Third case,
case 1" above. Purely additive (one new interface implementation, four new methods, two new fields); no
existing method on that class changed signature or behaviour.

**Could not be verified in-game** (no compiler feedback per the big-bang rule, and several dependencies are
still other waves' responsibility): the whole terminal live-update path end-to-end, since it depends on
agent 4-1's `PacketMEInventoryUpdate`/`GridInventoryEntry` (already landed, read and confirmed compatible)
and agent 4-4's client-side `GuiMEMonitorable.postUpdate(List<GridInventoryEntry>)`/`ItemRepo` (not yet
confirmed landed at the time of this writing - `postUpdate`'s call site here is written against the shape
CONTRACT.md pins, not against a file this wave has read). `ContainerFluidInterfaceConfigurationTerminal`
will not compile until wave 5 replaces `appeng.fluids.util`'s internals, as noted above - expected, not a
gap in this wave's own work.

### Wave 4d — appeng.client (done)

24 files: `AEBaseGui`, `AEBaseMEGui`, `AEGuiHandler` (`client/gui`); `GuiCraftConfirm`, `GuiCraftingCPU`,
`GuiCraftingStatus`, `GuiExpandedProcessingPatternTerm`, `GuiFluidInterfaceConfigurationTerminal`,
`GuiInterfaceConfigurationTerminal`, `GuiInterfaceTerminal`, `GuiMEMonitorable`, `GuiNetworkStatus`,
`GuiPatternTerm`, `GuiUpgradeable` (`client/gui/implementations`); `ItemRepo`, `FluidRepo`, `SlotME`,
`SlotFluidME`, `InternalSlotME`, `InternalFluidSlotME` (`client/me`); `TesrRenderHelper`,
`StackSizeRenderer`, `CraftingMonitorTESR`, `AssemblerFX` (`client/render`). Read against the actual (not
guessed) shapes agents 4-1/4-2/4-3 had already landed by the time this wave started - `GridInventoryEntry`,
`PacketInventoryAction`'s two pinned constructors, `CraftingCPUStatus.getCrafting(): GenericStack`,
`ContainerNetworkStatus`/`ContainerCraftConfirm`/`ContainerCraftingCPU`'s `postUpdate(List<GridInventoryEntry>, ...)`
- all confirmed by reading those files, not assumed.

```java
// appeng.client.me.ItemRepo — the terminal's client-side model
// Map<AEKey, GridInventoryEntry> entries (was IItemList<IAEItemStack>) keyed by AEKey.equals(), which is
// already size-insensitive identity - the old list.findPrecise(is)-then-reset-then-add dance collapses to
// a plain put()/remove() (see postUpdate). All eight SearchBoxMode values, the "@" mod-name prefix, "-"/"!"
// term negation, Settings.SEARCH_TOOLTIPS, ViewItems.ALL/CRAFTABLE/STORED (the CRAFTABLE "zero copy" trick
// is now entry.withStoredAmount(0)), SortOrder.MOD/AMOUNT/INVTWEAKS/NAME, the bogosort integration with
// ItemSorters.CONFIG_BASED_SORT_BY_INV_TWEAKS fallback, and the JEI/HEI search-text bridge
// (Integrations.jei().setSearchText) are all preserved verbatim - only the element type changed.
// clear() keeps its old IItemList.resetStatus() semantics (zero every row's amounts/craftable flag, keep
// the keys) for GuiNetworkStatus's full-repopulate case; this is deliberately NOT the same as postUpdate's
// per-key put/remove, which is instructed by GridInventoryEntry.isMeaningful() to actually drop dead rows.
public GridInventoryEntry getReferenceItem(int idx);          // was IAEItemStack
public void postUpdate(GridInventoryEntry entry);              // was postUpdate(IAEItemStack)
public long getItemCount(AEKey what);                          // was getItemCount(IAEItemStack)
public void setViewCell(ItemStack[] list);                      // ItemViewCell.createFilter -> AEKeyFilter
public void clear();                                            // zeroes rows, keeps keys (see above)
// private static final class KeyAmountEntry implements Object2LongMap.Entry<AEKey> — a 3-method adapter
// (getKey/getLongValue/setValue) so appeng.util.ItemSorters's Object2LongMap.Entry<AEKey>-shaped
// comparators (built in wave 1a for iterating a KeyCounter) can also sort the GridInventoryEntry rows this
// repo holds, without touching the wave 1 file that defines them.

// appeng.client.me.FluidRepo — same design, the fluid terminal's simpler pre-port feature set preserved
// exactly (mod-prefix + tooltip search, no term negation, no JEI bridge, no view-cell filter - none of
// those existed here before this port either). NOTE for wave 5 (see the file's header comment):
// appeng.fluids.util.FluidSorters still exposes Comparator<IAEFluidStack> (a deleted api type, because
// appeng.fluids is wave 5's whole package); this file is written assuming FluidSorters gets the exact
// CONFIG_BASED_SORT_BY_MOD/_BY_SIZE/_BY_NAME -> Comparator<Object2LongMap.Entry<AEKey>> retyping
// appeng.util.ItemSorters already got in wave 1a, since the old FluidRepo used exactly those three names.

// appeng.client.me.SlotME / InternalSlotME
public GridInventoryEntry getEntry();           // was getAEStack(): IAEItemStack
// InternalSlotME.getStack() now wraps via AEKey.wrapForDisplayOrFilter() (identity/count-1 display,
// exactly what asItemStackRepresentation() gave) - the real amount is still drawn separately by
// StackSizeRenderer, unchanged division of responsibility.

// appeng.client.me.SlotFluidME / InternalFluidSlotME
public GenericStack getGenericStack();          // was getAEFluidStack(): IAEFluidStack
// NOTE for wave 5 (see the file's header comment): appeng.fluids.container.slots.IMEFluidSlot still
// declares IAEFluidStack getAEFluidStack() (a deleted api type). Written assuming IMEFluidSlot gets
// GenericStack getGenericStack(), mirroring the exact rename appeng.util.inv.ItemSlot already got in wave
// 1a (getAEItemStack() -> getGenericStack()). ISpecialSlotIngredient.getIngredient() now builds a raw
// FluidStack from the wrapped AEFluidKey (fluidKey.toStack(amount)) for HEI's ingredient-under-mouse hook.

// appeng.client.gui.AEBaseGui — the ME-slot click/wheel/drag routing
// Every SlotME branch (space-click MOVE_REGION, PICKUP_OR_SET_DOWN/AUTO_CRAFT, QUICK_MOVE/SHIFT_CLICK,
// CLONE/AUTO_CRAFT/CREATIVE_DUPLICATE, mouse-wheel ROLL_UP/ROLL_DOWN) now reads a GridInventoryEntry and
// calls AEBaseContainer.setTargetStack(entry.getWhat()) - was IAEItemStack throughout. The literal-`0`
// PacketInventoryAction(...) call sites were NOT touched: they were already resolving to the untouched
// (int slot, long id) overload (int 0 widens to long, it never matched the IAEItemStack/GenericStack
// overload), confirmed by reading the pre-migration file - a real trap this wave checked for and did not
// fall into. The IMEFluidSlot rendering branch reads GenericStack via getGenericStack() (see SlotFluidME's
// note) and dispatches fluid.getColor()/getStill() off the unwrapped AEFluidKey. The two ad hoc
// AEItemStack.fromItemStack(...) calls that existed purely to feed StackSizeRenderer a count (drag-split
// preview, encoded-pattern output preview) became GenericStack.fromItemStack(...).
// drawSlot's JEI/HEI hooks, IJEITargetSlot resolution, and the double-click/hotbar-swap logic are unchanged
// - none of them ever touched a storage type. HEI is a drop-in JEI fork (§8.2); no `mezz.jei` import or
// package name was renamed, per the brief.

// appeng.client.gui.AEBaseMEGui — the "N stored / N requestable / craftable" tooltip
// getCountRequestable() -> getRequestableAmount(), getStackSize() -> getStoredAmount(), otherwise identical.

// appeng.client.gui.AEGuiHandler — the JEI/HEI IAdvancedGuiHandler/IGhostIngredientHandler adapter
// The `List<IAEItemStack> visual` field this class read from GuiCraftConfirm/GuiCraftingCPU is now
// `List<AEKey>`; `visual.get(idx).getDefinition()` (the ingredient handed to JEI/HEI under the mouse)
// became `visual.get(idx).wrapForDisplayOrFilter()`. Ghost-slot target resolution (getTargets,
// IJEIGhostIngredients/IJEITargetSlot dispatch for GuiUpgradeable/GuiPatternTerm/
// GuiExpandedProcessingPatternTerm) is untouched - it was never storage-typed.

// appeng.client.gui.implementations.GuiMEMonitorable — "the terminal"
public void postUpdate(List<GridInventoryEntry> list);   // was List<IAEItemStack>; loops repo.postUpdate(entry)
// Every other named mechanic (SEARCH_MODE-driven autofocus/JEI-memory-text, TerminalStyle SMALL/FULL row
// math, the view-cell slots and craftingStatusBtn tab, the wireless/portable/chest/security-station name
// branching) reads no storage type directly - it all goes through ItemRepo/SlotME, already covered above.

// appeng.client.gui.implementations.GuiNetworkStatus — reuses ItemRepo for machines, not items (CONTRACT.md
// §10): GridInventoryEntry.getStoredAmount() is a machine count, getRequestableAmount() is that machine's
// idle power drain x100 (GuiText.EnergyDrain, Platform.formatPowerLong(..., true) - formatPowerLong already
// divides by 100 internally, confirmed by reading it, so no extra scaling was added here). NOT "fixed" into
// an item count, per the brief's explicit warning.
public void postUpdate(List<GridInventoryEntry> list);   // was List<IAEItemStack>

// appeng.client.gui.implementations.GuiCraftConfirm / GuiCraftingCPU — the used/missing/to-craft and the
// stored/active/scheduled columns, three KeyCounters each (was three IItemList<IAEItemStack>s) plus a
// List<AEKey> visual (was List<IAEItemStack>, mutated in place via a findPrecise/copy/setStackSize dance
// that a KeyCounter's map semantics make unnecessary - a plain KeyCounter.set(key, amount) per ref channel,
// and the per-key total read fresh off all three counters at draw time, replace it exactly). Both classes'
// three-channel byte-ref switch (0/1/2, the third only for a simulated craft) is untouched.
public void postUpdate(List<GridInventoryEntry> list, byte ref);   // was List<IAEItemStack>, byte ref
public List<AEKey> getVisual();                                    // was List<IAEItemStack>

// appeng.client.gui.implementations.GuiCraftingStatus — the named-CPU selector list and per-CPU progress
// bars (a fork/GTNH feature, no upstream equivalent). CraftingCPUStatus.getCrafting() is now GenericStack
// (confirmed against the already-landed wave 4b file); .getStackSize() -> .amount(),
// .createItemStack() -> GenericStack.wrapInItemStack(...). CPU selector list, scrollbar, hover tooltip and
// the coloured selection/hover states are otherwise untouched.

// appeng.client.gui.implementations.GuiPatternTerm / GuiExpandedProcessingPatternTerm — JEI/HEI ghost-item
// placement into pattern slots. getPhantomTargets' AEItemStack.fromItemStack(itemStack) -> GenericStack.
// fromItemStack(itemStack); PacketInventoryAction(PLACE_JEI_GHOST_ITEM, (SlotFake) slot, GenericStack).
// GuiExpandedProcessingPatternTerm (the expanded processing pattern terminal, no upstream equivalent) keeps
// its own background/button layout untouched - only the same one-line ghost-item swap applied.

// appeng.client.gui.implementations.GuiUpgradeable — the import/export bus JEI/HEI ghost-item placement,
// including the fluid-into-item-slot smuggling case named in the brief. The SlotFake+item branch and the
// SlotFake+GuiCellWorkbench+fluid-as-bucket branch both became GenericStack.fromItemStack(...) (the bucket
// item itself is unaffected - FluidUtil.getFilledBucket(fluidStack) still builds a real bucket ItemStack,
// only the packet payload wrapping changed). The true fluid-slot branch (GuiFluidSlot) no longer smuggles
// AEFluidStack.fromFluidStack(...).asItemStackRepresentation() through a dummy item's NBT - it builds
// `new GenericStack(AEFluidKey.of(finalFluidStack), finalFluidStack.amount)` and the packet carries the
// fluid key directly, per the brief. Settings.SCHEDULING_MODE, redstone/fuzzy/craft-only mode buttons and
// GuiCellWorkbench (a subclass not on this wave's file list, confirmed by grep to reference no storage type
// at all - see "changed outside the file list" below) are untouched.

// appeng.client.gui.implementations.GuiInterfaceTerminal / GuiInterfaceConfigurationTerminal — two of the
// three terminal parts with no upstream equivalent (Settings.INTERFACE_TERMINAL-gated). Both had exactly
// one old-API line each: Platform.getItemDisplayName(AEApi...getStorageChannel(IItemStorageChannel.class)
// .createStack(parsedItemStack)) collapsed to Platform.getItemDisplayName(parsedItemStack) directly (per
// wave 1a, Platform.getItemDisplayName now accepts a plain ItemStack or an AEKey). GuiInterfaceConfigurationTerminal
// additionally had one JEI ghost-item line (AEItemStack.fromItemStack -> GenericStack.fromItemStack). Every
// named/searched/highlighted-interface mechanic (byId/byName maps, broken-pattern detection, dimension
// highlighting, the input/output/name search fields) reads no other storage type and is untouched.

// appeng.client.gui.implementations.GuiFluidInterfaceConfigurationTerminal — the third terminal part with no
// upstream equivalent. NOTE for wave 5 (see the file's header comment): appeng.fluids.util.IAEFluidTank
// still declares IAEFluidStack getFluidInSlot(int)/setFluidInSlot(int, IAEFluidStack) (a deleted api type).
// Written assuming GenericStack getFluidInSlot(int)/setFluidInSlot(int, GenericStack), mirroring the rename
// appeng.tile.inventory.AppEngInternalAEInventory already got in wave 2 (getAEStackInSlot(int): GenericStack)
// - independently confirmed as the right shape by wave 4c's ContainerFluidInterfaceConfigurationTerminal
// entry, which names the identical requirement. The §9.1 hazard bit here for real: matchedStacks used to
// hold whole fluid-stack objects and compare them with .contains(...); translated to holding bare AEKeys
// (identity only) instead of GenericStack, because a tank's amount can change between when a slot's
// contents were matched by search and when drawFG re-checks membership for highlighting - see the audit
// below. postUpdate's NBT parse (AEFluidStack.fromNBT -> GenericStack.readTag) and the ghost-item line
// (AEItemStack.fromItemStack -> GenericStack.fromItemStack) round out the file's changes; the tank-slot
// layout, dimension highlighting and name search are untouched.

// appeng.client.render.TesrRenderHelper — pinned by wave 3's AbstractPartMonitor.renderDynamic
public static void renderItem2dWithAmount(AEItemKey what, long amount, float itemScale, float spacing);
public static void renderFluid2dWithAmount(AEFluidKey what, long amount, float scale, float spacing);
// what.toStack()/what.toStack(amount) replace asItemStackRepresentation()/getFluidStack() for the icon;
// the amount is now a plain long parameter instead of read off the stack. renderItem2d/renderFluid2d
// (the actual 2D-flattened GL drawing) and moveToFace/rotateToFace are untouched.

// appeng.client.render.StackSizeRenderer — the abbreviated K/M/G stack-count overlay
public void renderStackSize(FontRenderer fr, @Nullable GridInventoryEntry entry, int x, int y);  // ME slots
public void renderStackSize(FontRenderer fr, @Nullable GenericStack stack, int x, int y);         // ad hoc
// Two overloads instead of one IAEItemStack-typed method, because the old single method actually served two
// different old callers: a live ME slot's IAEItemStack (which carried isCraftable(), driving the "Craft"
// label in place of a zero count) and a throwaway AEItemStack.fromItemStack(...) built purely to carry a
// count (drag-split preview, encoded-pattern output preview - never craftable). Both delegate to the same
// private long/boolean core so the large-font/slim-font/craft-label logic is identical either way. The
// config-driven font size, the Alt-key "always show Craft" behaviour, and the K/M abbreviation are untouched.

// appeng.client.render.crafting.CraftingMonitorTESR — the crafting monitor's rendered job
// TileCraftingMonitorTile.getJobProgress() is a GenericStack (confirmed by reading the file - already
// GenericStack since wave 2). The renderer now dispatches on jobProgress.what() instanceof AEItemKey/
// AEFluidKey and calls the matching TesrRenderHelper overload, rather than assuming an item - this is
// completing wave 2's generalisation, not new scope: the old renderer could only ever depict an item job
// because the channel-per-type model kept item/fluid crafting monitors apart, and nothing about the fork's
// mechanic set is added or changed by letting a fluid crafting job's progress render too.

// appeng.client.render.effects.AssemblerFX — the molecular assembler's crafting particle
public AssemblerFX(World w, double x, double y, double z, double r, double g, double b, float speed, GenericStack is);
// was IAEItemStack is - pinned by PacketAssemblerAnimation(BlockPos, byte, GenericStack) (wave 4-1).
// GenericStack.wrapInItemStack(is) replaces is.asItemStackRepresentation() for the floating-item display
// (EntityFloatingItem); ClientHelper.java (not on this wave's file list) needed no change at all, since it
// only forwards PacketAssemblerAnimation's already-GenericStack-typed `is` field straight through.
```

**Fork-specific mechanics from point 10, verified preserved:** all eight `SearchBoxMode` values, the `@`
mod-name prefix, `-`/`!` term negation, `Settings.SEARCH_TOOLTIPS`, `ViewItems.ALL/CRAFTABLE/STORED`,
`SortOrder.MOD/AMOUNT/INVTWEAKS/NAME`, the bogosort integration (with `ItemSorters.CONFIG_BASED_SORT_BY_INV_TWEAKS`
fallback) and the JEI/HEI search-text bridge (`ItemRepo`); the "N craftable/N requestable" tooltip lines
(`AEBaseMEGui`); `GuiNetworkStatus` reusing `GridInventoryEntry` for a machine count and an idle-power-drain
(not an item count); the used/missing/to-craft three-channel split (`GuiCraftConfirm`) and the
stored/active/scheduled split (`GuiCraftingCPU`); the named-CPU selector and per-CPU progress bars
(`GuiCraftingStatus`); the JEI/HEI ghost-item mechanic end to end, including the fluid-into-item-slot
smuggling case now replaced by a direct fluid key (`GuiPatternTerm`/`GuiExpandedProcessingPatternTerm`/
`GuiUpgradeable`/`GuiInterfaceConfigurationTerminal`); `Settings.SCHEDULING_MODE` and the
`FluidUtil.getFilledBucket` branch (`GuiUpgradeable`); the three `Settings.INTERFACE_TERMINAL`-gated
terminal parts with no upstream equivalent, including `PATTERN_EXPANSION` slot-count display, which lives
entirely in the containers these GUIs open (agents 4-2/4-3's files) and was not touched here
(`GuiInterfaceTerminal`/`GuiInterfaceConfigurationTerminal`/`GuiFluidInterfaceConfigurationTerminal`); the
molecular assembler's crafting particle and rate (`AssemblerFX`); the crafting monitor's rendered job,
extended (not cut) to cover a fluid job now that `GenericStack` makes that expressible
(`CraftingMonitorTESR`). HEI is a drop-in JEI fork (§8.2) - no `mezz.jei` import, package name or class name
was renamed anywhere in this wave's files, per the brief.

**§9.1 `.equals(` audit, all 24 files:** grepped every file for `.equals(`. Real hits, all safe: `String.equals`
(`entry.getUnlocalizedName().equals(MOLECULAR_ASSEMBLER)` in `GuiInterfaceTerminal`, `lastSearch.equals(searchString)`
in `ItemRepo` - neither is a stack comparison). **One real §9.1 instance found and fixed:**
`GuiFluidInterfaceConfigurationTerminal.matchedStacks` used to be populated with, and checked via
`.contains(...)` against, whole fluid-stack objects; translated to holding bare `AEKey`s (`fs.what()`, not
`fs`) precisely because a tank's amount can drift between the search pass (`refreshList`) and the
highlight-membership check a frame later (`drawFG`), which would have made `GridInventoryEntry`/`GenericStack`'s
amount-sensitive `equals()` intermittently miss a slot that plainly still holds the searched-for fluid.
**No whole-`GenericStack` (or `GridInventoryEntry`) comparison exists anywhere in this wave's 24 files** -
every remaining identity check is a map lookup/`.contains()` keyed by `AEKey` (whose `equals()` never
carried an amount to begin with, per the §9.1 rule's own scope) or a plain `List<AEKey>`/`Map<AEKey, ...>`
operation.

**API gaps hit: none.** Every method this wave needed - `AEKey.wrapForDisplayOrFilter`, `GenericStack.fromItemStack`/
`wrapInItemStack`/`readTag`, `AEItemKey.toStack`/`.of`, `AEFluidKey.of`/`.toStack`/`.getFluid`, `KeyCounter.get`/
`.set`/`.clear`, `Platform.getItemDisplayName`/`getFluidDisplayName`/`getModId`/`getTooltip`,
`ItemViewCell.createFilter` - was already in `src/api` or `appeng.util.Platform` from earlier waves, exactly as
specified. `src/api` was not touched.

**Mechanics that could not be preserved: none.** Every mechanic named in the brief (point 10) and every one
found by reading each file's pre-migration behaviour before rewriting it (per the brief's method) has a
direct translation in the new model.

**Changed outside the assigned file list: none edited.** `appeng.client.gui.implementations.GuiCellWorkbench`
(a `GuiUpgradeable` subclass, not on this wave's file list) was read because `GuiUpgradeable.getPhantomTargets`
branches on `instanceof GuiCellWorkbench`; confirmed by grep to reference no deleted storage type at all
(it only calls `ContainerCellWorkbench` accessors unrelated to `AEKey`/`GenericStack`), so it needed no
change and none was made. `appeng.client.me.ClientDCInternalInv`/`SlotDisconnected`/`ClientDCInternalFluidInv`
(used by the interface-terminal GUIs, not on this wave's file list) were read for the same reason and are
similarly clean, except `ClientDCInternalFluidInv.getInventory(): IAEFluidTank`, which is wave 5's problem
(see below). `appeng.client.ClientHelper` (constructs `AssemblerFX`) was read and needed no change - it
only forwards `PacketAssemblerAnimation`'s already-`GenericStack`-typed field through.

**Cross-wave dependencies this wave's files now assume (predicted, not guessed at random - each mirrors an
already-landed rename elsewhere in the tree), left for wave 5 to satisfy:**

- `appeng.fluids.container.slots.IMEFluidSlot.getAEFluidStack(): IAEFluidStack` must become
  `getGenericStack(): GenericStack` - mirrors `appeng.util.inv.ItemSlot`'s wave 1a rename exactly.
- `appeng.fluids.util.IAEFluidTank.getFluidInSlot(int): IAEFluidStack`/`setFluidInSlot(int, IAEFluidStack)`
  must become `GenericStack`-typed - mirrors `AppEngInternalAEInventory.getAEStackInSlot(int): GenericStack`
  (wave 2), and is independently required by wave 4c's `ContainerFluidInterfaceConfigurationTerminal` entry.
- `appeng.fluids.util.FluidSorters`'s `Comparator<IAEFluidStack>` fields must become
  `Comparator<Object2LongMap.Entry<AEKey>>` - mirrors `appeng.util.ItemSorters`'s wave 1a retyping exactly
  (same three field names: `CONFIG_BASED_SORT_BY_MOD`/`_BY_SIZE`/`_BY_NAME`).
- `appeng.fluids.client.render.FluidStackSizeRenderer.renderStackSize` must gain a
  `(FontRenderer, GenericStack, int, int)` overload - mirrors this wave's own `StackSizeRenderer` exactly
  (no craftable flag involved on the fluid side, confirmed by reading the pre-migration file).

None of these four are guesses in the sense of "hoped for" - each already has a concrete precedent
elsewhere in the already-landed tree (`ItemSlot`, `AppEngInternalAEInventory`, `ItemSorters`, this wave's own
`StackSizeRenderer`) that the wave 5 agent can point at directly instead of inventing a shape.

**The multi-type filter GUI and the `AEKeyType` button icons (STATUS.md debt list) - status, precisely:**
this wave did **not** build a new screen or new textures, per the brief's explicit instruction. What it did
do: every GUI-side slot/repo/render path that used to be hardcoded to `IAEItemStack` (`ItemRepo`/`SlotME`/
`GridInventoryEntry`-consuming code, `AEBaseGui`'s slot-click routing, `StackSizeRenderer`,
`TesrRenderHelper`, `CraftingMonitorTESR`, `AssemblerFX`) is now generic over `AEKey`/`GenericStack` and
renders any key type through the existing `AEKey.wrapForDisplayOrFilter()`/`GenericStack.wrapInItemStack()`
machinery (real item for `AEItemKey`, the `WrappedGenericStack` placeholder item for everything else, per
wave 3d). Once wave 5 registers the fluid strategies, the same terminal (`GuiMEMonitorable`) will show fluid
rows mixed in with no further change to this wave's files - that generic-display half of "the multi-type
filter GUI" is what this wave delivers. What it does **not** deliver, and could not verify without a
build/run (per the brief's own caution that "rendering in particular cannot be verified without a build"):
(1) `WrappedGenericStack` still has no client-side model/texture registered (confirmed by grep - unchanged
since wave 3d), so any slot that ends up wrapping a non-item key today will render with the item's
default/missing texture rather than the wrapped content's own icon; the established precedent this wave
did **not** implement is `appeng.fluids.items.FluidDummyItemRendering` (GUI code drawing the wrapped
content's icon directly instead of a vanilla item model) - flagged here exactly as the brief asked, left for
whoever designs the actual multi-type GUI. (2) No file in `appeng.client` calls `AEKeyType.getButtonTexture()`/
`getButtonIcon()` at all (confirmed by grep across the whole package) - the placeholder icons set in wave 2's
`AEItemKeyType`/`AEFluidKeyType` have no GUI consumer anywhere yet, so there was nothing in this wave's file
list to wire up or replace; they remain exactly the placeholders STATUS.md already lists.

### Wave 5 prerequisites — done by hand before the wave, do not redo

Six files in `appeng.fluids` are already in the tree, migrated by hand. **Do not rewrite them.** They are the points where wave 5's agents meet each other, and the points where wave 4's *already committed* code reaches into `appeng.fluids` — so their shape is not a proposal, it is a constraint that committed code depends on. Read them, call them, and if one looks wrong, stop and report rather than changing it.

```java
// appeng.fluids.util.IAEFluidTank                          (agent 5-B implements; A, C, D all call it)
void setFluidInSlot(int slot, @Nullable GenericStack fluid);   // was IAEFluidStack
@Nullable GenericStack getFluidInSlot(int slot);               // was IAEFluidStack
int getSlots();                                                // unchanged

// appeng.fluids.container.IFluidSyncContainer               (agent 5-C implements)
void receiveFluidSlots(Map<Integer, GenericStack> fluids);     // was Map<Integer, IAEFluidStack>

// appeng.fluids.container.slots.IMEFluidSlot                (agent 5-C implements; 5-D implements it too)
@Nullable GenericStack getGenericStack();                      // was IAEFluidStack getAEFluidStack()
default boolean shouldRenderAsFluid();                         // unchanged

// appeng.fluids.helper.FluidSyncHelper                      (agent 5-C owns; fully migrated already)
void readPacket(Map<Integer, GenericStack> data);
// sendFull/sendDiff(Iterable<IContainerListener>) unchanged

// appeng.fluids.util.FluidSorters                           (agent 5-B owns; fully migrated already)
static final Comparator<Object2LongMap.Entry<AEKey>> CONFIG_BASED_SORT_BY_NAME;  // was Comparator<IAEFluidStack>
static final Comparator<Object2LongMap.Entry<AEKey>> CONFIG_BASED_SORT_BY_MOD;
static final Comparator<Object2LongMap.Entry<AEKey>> CONFIG_BASED_SORT_BY_SIZE;
static void setDirection(SortDir direction);                   // unchanged

// appeng.fluids.client.render.FluidStackSizeRenderer        (agent 5-D owns; fully migrated already)
void renderStackSize(FontRenderer fontRenderer, GenericStack stack, int xPos, int yPos);  // was IAEFluidStack
```

**An empty slot is `null`, exactly as it was.** `GenericStack` has no "empty" instance and `GenericStack.writeTag`/`readTag` already round-trip `null`. Do not invent an empty sentinel.

**The key in these `GenericStack`s is not statically an `AEFluidKey`.** `GenericStack` is type-erased, so every implementor in `appeng.fluids` still holds fluids only, but code that needs the `FluidStack` back must pattern-match: `stack.what() instanceof AEFluidKey fk ? fk.toStack((int) stack.amount()) : null`.

**`FluidSyncHelper.equalsSlot` is the inverse of the §9.1 hazard and is already correct.** The old code compared twice — `Objects.equals` (size-insensitive for `IAEFluidStack`) *and then* the stack sizes — precisely because an amount change had to count as a change. The record's `equals` already compares the amount, so the single `Objects.equals` preserves the old behaviour exactly. Do not "fix" it into a key-only comparison; a config slot whose amount changed would stop syncing.

Two more shapes are pinned by committed wave-4 code but were **not** migrated by hand, because they are implementation-heavy and belong to one agent each. Their signatures are still fixed:

```java
// appeng.fluids.util.AEFluidInventory                       (agent 5-B; implements IAEFluidTank)
AEFluidInventory(@Nullable IAEFluidInventory handler, int slots);              // 2-arg and
AEFluidInventory(@Nullable IAEFluidInventory handler, int slots, int stackSize); // 3-arg ctors both stay
void readFromNBT(NBTTagCompound data, String name);   // both still exist, both still keyed by name;
void writeToNBT(NBTTagCompound data, String name);    // the per-slot payload becomes GenericStack.writeTag/readTag

// appeng.fluids.container.ContainerFluidConfigurable        (agent 5-C)
IAEFluidTank getFluidConfigInventory();               // return type unchanged, element type now GenericStack
```

Callers already committed against all of the above, in wave 4 — **read them before writing, they are the specification**: `appeng.container.implementations.ContainerFluidInterfaceConfigurationTerminal`, `appeng.client.me.ClientDCInternalFluidInv`, `appeng.client.me.SlotFluidME`, `appeng.client.me.FluidRepo`, `appeng.client.gui.AEBaseGui#drawSlot`, `appeng.core.sync.packets.PacketFluidSlot`, `appeng.core.sync.packets.PacketInventoryAction` (the `PLACE_JEI_GHOST_ITEM` branch), `appeng.parts.AEBasePart#readFromNBT/writeToNBT` and `appeng.tile.AEBaseTile#readFromNBT/writeToNBT`.

**Three files in `appeng.fluids.util` are deletions, not migrations**: `AEFluidStack`, `FluidList` and `MeaningfulFluidIterator`. Their item-side counterparts (`AEItemStack`, `ItemList`, `MeaningfulItemIterator` in `appeng.util.item`) were deleted in wave 0; `AEFluidKey` and `KeyCounter` replace all six. Anything still importing them is a call site to migrate, not a reason to keep them.

**Wave 5's agent split, and who owns each shared hub.** Four agents on disjoint file lists:

| Agent | Files |
|---|---|
| 5-A | `appeng.fluids.parts` (9), `appeng.fluids.registries` (1), `appeng.fluids.items` (1), plus the two registration lines described below |
| 5-B | `appeng.fluids.util` (6 left) and `appeng.fluids.helper` (2 left) |
| 5-C | `appeng.fluids.container` (6 left) |
| 5-D | `appeng.fluids.client` (5 left) |

Three types are called across that boundary. **The owner writes them; everyone else calls them and does not edit them.**

```java
// appeng.fluids.helper.DualityFluidInterface                (5-B owns; 5-A and 5-C call it)
// Follow appeng.helpers.DualityInterface (wave 2) exactly: it dropped the deleted IStorageMonitorable
// and now `implements MEStorage` directly, exposing itself through an inner Accessor that implements
// IStorageMonitorableAccessor. Do the same here. These members are called by ALREADY COMMITTED wave-4
// code and must keep their names and return types:
IAEFluidTank getConfig();          // wave 4 casts the result to AEFluidInventory - keep it an AEFluidInventory
IAEFluidTank getTanks();
IConfigManager getConfigManager(); // still registers Settings.INTERFACE_TERMINAL
String getTermName();
long getSortValue();
DimensionalCoord getLocation();

// appeng.fluids.helper.IFluidInterfaceHost                  (5-B owns; 5-A implements it)
DualityFluidInterface getDualityFluidInterface();
default void onStackReturnNetwork(GenericStack stack);   // was IAEFluidStack

// appeng.fluids.parts.PartFluidLevelEmitter                 (5-A owns)
void setReportingValue(long);   // called by the committed appeng.parts.AEBasePart#readFromNBT
```

**Agent 5-A also owns the two strategy-registration lines, and they are the point of the wave.** `appeng.parts.automation.InitStackWorldBehaviors.register()` and `appeng.parts.misc.InitExternalStorageStrategies.register()` are committed files outside `appeng.fluids`; 5-A adds the `AEKeyType.fluids()` registrations to them, mirroring the `AEKeyType.items()` lines already there, and no other agent may touch either file. Once those five strategies are registered, the **already migrated** generic `PartImportBus`, `PartExportBus`, `PartAnnihilationPlane`, `PartAbstractFormationPlane` and `PartStorageBus` handle fluids with no further change. Do not add fluid branches to those wave-3 parts.

### Wave 5d — appeng.fluids.client (done)

Five files: `GuiFluidTerminal`, `GuiMEPortableFluidCell` (`fluids/client/gui`); `GuiFluidSlot`, `GuiFluidTank`, `GuiOptionalFluidSlot` (`fluids/client/gui/widgets`). `appeng.fluids.client.render.FluidStackSizeRenderer` was already migrated by hand before the wave (CONTRACT.md's "Wave 5 prerequisites") and was read, not edited. `appeng.fluids.client.gui.GuiWirelessFluidTerminal` — the sixth file STATUS.md's file count implies for this package — is a trivial `GuiMEPortableFluidCell` subclass that overrides only `drawBG`/`getJEIExclusionArea`; it inherits `postUpdate(List<GridInventoryEntry>)` unchanged from its parent and needed no edit, confirmed by reading it.

```java
// appeng.fluids.client.gui.GuiFluidTerminal / GuiMEPortableFluidCell — identical shape, ported in lockstep
public void postUpdate(final List<GridInventoryEntry> list);   // was List<IAEFluidStack>; loops repo.postUpdate(entry)
// renderHoveredToolTip: IMEFluidSlot.getAEFluidStack() -> getGenericStack(); the tooltip's fluid name, mod
// name and "<amount> B" line now read off the unwrapped AEFluidKey (stack.what() instanceof AEFluidKey fk),
// required because GenericStack is type-erased - shouldRenderAsFluid() is still checked first, exactly as
// before, so a slot that opts out of fluid rendering never reaches the pattern match. Platform.getModId(AEKey)
// and Platform.getFluidDisplayName(AEKey) (both already generalised in wave 1a) replace the old
// Platform.getModId(IAEFluidStack) and fluidStack.getFluidStack().getLocalizedName() calls one for one -
// AEFluidKey.computeDisplayName() is exactly fluid.getLocalizedName(stack) at a 1-bucket amount, so the
// displayed name is unchanged.
// handleMouseClick: meSlot.getAEFluidStack() -> meSlot.getGenericStack(); container.setTargetStack(AEKey)
// replaces container.setTargetStack(IAEFluidStack) - the container is assumed to expose the AEKey-typed
// overload PacketTargetFluidStack already dispatches to (see "assumptions about the server side" below).
// The debug logging (`AELog.debug("mouse0/mouse1 GUI STACK SIZE %s", ...)`) now reads stack.amount() instead
// of stack.getStackSize() - same log, same call sites, same null-guard shape in the mouse1/EMPTY_ITEM branch.

// appeng.fluids.client.gui.widgets.GuiFluidSlot — the JEI/HEI ghost-fluid-drop target used by the import/
// export bus and fluid interface config screens
@Nullable GenericStack getFluidStack();                 // was IAEFluidStack; delegates to IAEFluidTank
void setFluidStack(@Nullable GenericStack stack);        // was IAEFluidStack; sends PacketFluidSlot(Map<Integer, GenericStack>)
// drawContent/getMessage/getIngredient all pattern-match stack.what() instanceof AEFluidKey fk to recover the
// Fluid/FluidStack the old IAEFluidStack carried directly - fk.getFluid() for the sprite/color, fk.toStack(...)
// for the HEI ingredient-under-mouse hook (mirrors SlotFluidME.getIngredient() exactly, including the
// Math.min(amount, Integer.MAX_VALUE) cast-safety guard). slotClicked's AEFluidStack.fromFluidStack(fluid) ->
// GenericStack.fromFluidStack(fluid), the only other call site touched.

// appeng.fluids.client.gui.widgets.GuiFluidTank — the fluid-interface-configuration-terminal tank display
@Nullable GenericStack getFluidStack();                  // was IAEFluidStack; delegates to IAEFluidTank
// drawContent's fill-height math (amount / tank capacity, clamped to the widget's pixel height, drawn in
// 16px sprite strips plus a remainder strip) is untouched arithmetic, only reading fluid.amount() off the
// unwrapped GenericStack/AEFluidKey pair instead of fluid.getStackSize()/fluid.getFluid(). getMessage's
// tooltip is unchanged in shape and units: fluid display name, then "<raw amount>/<capacity>mB" on its own
// line - deliberately NOT divided by 1000 into buckets, unlike FluidStackSizeRenderer's slot-label overlay;
// this is the pre-port behaviour (the tank tooltip has always shown raw millibuckets against the tank's raw
// millibucket capacity) and nothing here changes that unit choice. slotClicked (FILL_ITEM/EMPTY_ITEM by
// slot+id, no stack payload) is untouched.

// appeng.fluids.client.gui.widgets.GuiOptionalFluidSlot — the storage-bus-style optional fluid slot group
@Override GenericStack getFluidStack();                  // was IAEFluidStack; only the override's return type changed
// isSlotEnabled/drawBackground (the enabled/disabled tint) are untouched; the "auto-clear a stack sitting in
// a slot that just got disabled" behaviour in getFluidStack() is preserved verbatim, now calling
// this.setFluidStack(null) with the GenericStack-typed setter from GuiFluidSlot.
```

**Fork-specific mechanics from point 5, verified preserved:** `Settings.SORT_BY`/`SORT_DIRECTION`/`VIEW_MODE`
(read through `ISortSource`/`IConfigManagerHost`, untouched in both terminal GUIs - `GuiFluidTerminal` and
`GuiMEPortableFluidCell` never had a `SEARCH_MODE` button or a JEI-memory-text bridge before this port either,
confirmed by reading the pre-migration files, so none was added); `Settings.ACTIONS` in `actionPerformed`'s
`iBtn.getSetting() != Settings.ACTIONS` guard (untouched, no storage type involved); the whole
`FLUID_TERMINAL`/portable-fluid-cell family (both GUIs, `ContainerFluidTerminal`/`ContainerMEPortableFluidCell`,
5-C's containers, called but not edited here); the fluid tank widget's tooltip and its raw-millibucket display
(`GuiFluidTank.getMessage()`, see above - explicitly kept in millibuckets, not converted to buckets); the
`FluidStackSizeRenderer`'s bucket-divided slot-count overlay, read but not edited, still invoked exactly once,
by the already-committed `AEBaseGui#drawSlot` - not by any file in this wave's list, since neither terminal
screen ever called it directly before this port either. No `FUZZY_MODE` reference exists in any of this
wave's five files (grep-confirmed); it was never part of this package's pre-port feature set.

**`.equals(` audit, all five files:** grepped every file for `.equals(`. **Zero hits in any of the five
files.** Every identity check in this wave's package is either a `null`/`instanceof AEFluidKey` guard or a
delegate to `IAEFluidTank.getFluidInSlot`/`setFluidInSlot` - nothing here ever compared two stacks or two
keys for equality, before or after the port. Nothing to translate, nothing to flag per the §9.1 hazard.

**Assumptions made about what the server side (5-C, `appeng.fluids.container`) sends or expects, stated
explicitly per the brief:**
- `ContainerFluidTerminal`/`ContainerMEPortableFluidCell` (and, by inheritance, `ContainerWirelessFluidTerminal`)
  expose `void setTargetStack(@Nullable AEKey stack)` - not `IAEFluidStack`. This is not a guess: it is pinned
  by the already-committed `appeng.core.sync.packets.PacketTargetFluidStack`, whose header comment and
  `serverPacketData` both call `((ContainerFluidTerminal) player.openContainer).setTargetStack(this.stack)`
  with `this.stack` typed `AEKey`. Both GUI files call `this.container.setTargetStack(stack == null ? null :
  stack.what())` against that exact shape.
- `IMEFluidSlot.getGenericStack()` (used by both terminal GUIs via `SlotFluidME`, and read directly in
  `renderHoveredToolTip`) is the already-migrated-by-hand prerequisite shape (`@Nullable GenericStack
  getGenericStack()`), not something this wave invented.
- `IAEFluidTank.getFluidInSlot(int)`/`setFluidInSlot(int, GenericStack)` (used by both `GuiFluidSlot` and
  `GuiFluidTank`, owned by agent 5-B) match the prerequisite shape verbatim, including the "empty slot is
  `null`, not an empty-sentinel `GenericStack`" rule.
- `PacketFluidSlot`'s constructor takes `Map<Integer, GenericStack>` (confirmed by reading the already-committed
  file directly, not assumed) - `GuiFluidSlot.setFluidStack` sends
  `Collections.singletonMap(this.getId(), this.getFluidStack())` unchanged in shape from the pre-port file,
  only the map's value type changed.
- `PacketInventoryAction`'s `(InventoryAction, IJEITargetSlot, GenericStack)` constructor already special-cases
  `GuiFluidSlot` by casting it directly (confirmed by reading the already-committed file) - `GuiFluidSlot`
  needed no change to satisfy that cast, since `getId()` was already inherited from `GuiCustomSlot`.

**Could not be verified without a build:** all rendering in this wave's files - the fluid sprite/color draw in
`GuiFluidSlot`/`GuiFluidTank`, the tooltip text layout, and the terminal's slot grid, none of which can be
exercised without launching the game. `ContainerFluidTerminal`/`ContainerMEPortableFluidCell`/
`ContainerWirelessFluidTerminal`'s actual `setTargetStack(AEKey)`/`postUpdate(List<GridInventoryEntry>)`
signatures (agent 5-C's files, not yet confirmed landed at the time of this writing) - this wave's GUI code is
written against the shape CONTRACT.md's wave-5-prerequisites section and the already-committed
`PacketTargetFluidStack`/`PacketMEFluidInventoryUpdate` pin, not against files this wave read directly.

**API gaps hit: none.** Every method needed - `GenericStack.fromFluidStack`, `AEFluidKey.getFluid`/`.toStack`,
`AEKey.getDisplayName`, `Platform.getModId`/`getFluidDisplayName`, `GridInventoryEntry.getWhat`/
`.getStoredAmount` (via `FluidRepo.postUpdate`, already migrated) - was already in `src/api` or
`appeng.util.Platform` from earlier waves. `src/api` was not touched.

**Mechanics that could not be preserved: none.** Every mechanic enumerated in the pre-migration read of all
five files (search field, sort-by/sort-direction buttons, view-mode-driven "zero copy" craftable rows via
`FluidRepo`, the scrollbar math, the JEI/HEI ghost-fluid-drop target, the tank fill-height/tooltip display,
the optional-slot enable/disable tint and auto-clear) has a direct translation in the new model.

**Changed outside the assigned file list: none.** `appeng.fluids.client.render.FluidStackSizeRenderer` and
`appeng.fluids.client.gui.GuiWirelessFluidTerminal` were both read but neither was edited: the former is the
frozen wave-5 prerequisite, and no file in this wave's list calls it directly - only the already-committed
`AEBaseGui#drawSlot` does; the latter is the trivial subclass discussed above.

### Wave 5b — appeng.fluids.util and appeng.fluids.helper (done)

Eight files: `AEFluidInventory`, `AEFluidTank`, `AENetworkFluidInventory` (migrated), `AEFluidStack`, `FluidList`,
`MeaningfulFluidIterator` (deleted, not migrated — their item-side counterparts went the same way in wave 0)
— all `appeng.fluids.util`; `DualityFluidInterface`, `IFluidInterfaceHost` — `appeng.fluids.helper`.
`IAEFluidTank`/`FluidSorters` (util) and `FluidSyncHelper` (helper) were already migrated by hand before the
wave and were read, not edited. `IAEFluidInventory` (util) and `IConfigurableFluidInventory`/`FluidCellConfig`
(helper) were read too — none references a deleted type, so none needed a change; they are not part of the
36-file count for this reason.

```java
// appeng.fluids.util.AEFluidInventory implements IAEFluidTank
AEFluidInventory(@Nullable IAEFluidInventory handler, int slots, int capcity);
AEFluidInventory(@Nullable IAEFluidInventory handler, int slots);              // capacity = Integer.MAX_VALUE
void setFluidInSlot(int slot, @Nullable GenericStack fluid);   // was IAEFluidStack
@Nullable GenericStack getFluidInSlot(int slot);               // was IAEFluidStack
void setCapacity(int capacity);
int fill(int slot, FluidStack resource, boolean doFill);       // per-slot IFluidHandler-style helpers, unchanged
FluidStack drain(int slot, FluidStack resource, boolean doDrain);   // signatures - callers pass/receive plain
FluidStack drain(int slot, int maxDrain, boolean doDrain);          // FluidStack, only the internal GenericStack
// + the whole-tank IFluidHandler surface (fill/drain over all slots, getTankProperties) unchanged in shape.
void readFromNBT(NBTTagCompound data, String name);   // per-slot payload now GenericStack.writeTag/readTag
void writeToNBT(NBTTagCompound data, String name);    // ("#"+slotIndex -> tag), same key scheme as before

// appeng.fluids.util.AEFluidTank extends FluidTank implements IAEFluidTank   — single-slot tank
// Not instantiated anywhere in the tree (grep-confirmed, no `new AEFluidTank(` call site) - migrated anyway
// per the brief (it's a migration target, not a deletion target), kept behaviourally identical.

// appeng.fluids.util.AENetworkFluidInventory extends AEFluidInventory
// Only fill() is network-aware (mirrors appeng.tile.inventory.AppEngNetworkInventory, which only overrides
// insertItem, not extraction). MEStorage.insert() returns the amount actually inserted, not the old
// IMEInventory.injectItems' leftover stack, so the three-way branch (nothing inserted -> store locally;
// partially inserted -> report the inserted amount and store nothing locally; fully inserted -> report the
// full stack) is the same leftover-math translated to inserted-math Platform.poweredInsert/poweredExtraction
// already use elsewhere in this migration.

// appeng.fluids.helper.IFluidInterfaceHost   (5-B owns; 5-A implements it)
DualityFluidInterface getDualityFluidInterface();
EnumSet<EnumFacing> getTargets();  TileEntity getTileEntity();  void saveChanges();   // unchanged
default void onStackReturnNetwork(GenericStack stack) {}   // was IAEFluidStack; still an inert default no-op
// (grep-confirmed: no override anywhere in the tree, before or after this migration)

// appeng.fluids.helper.DualityFluidInterface implements IGridTickable, MEStorage, IAEFluidInventory,
//         IAEAppEngInventory, IUpgradeableHost, IConfigManagerHost, IConfigurableFluidInventory
// Follows appeng.helpers.DualityInterface's post-migration shape exactly: drops the deleted
// IStorageMonitorable, implements MEStorage directly via a single getInventory(): MEStorage that returns
// either an InterfaceInventory (config/whitelist mode) or the network's MEStorage (no config) - and exposes
// itself through a private Accessor implements IStorageMonitorableAccessor, same pattern, same field name.
public static final int NUMBER_OF_TANKS = 9;                      // unchanged
public static final int TANK_CAPACITY = Fluid.BUCKET_VOLUME * 4;  // unchanged
IAEFluidTank getConfig();          // returns the AEFluidInventory field directly - wave 4's cast still holds
IAEFluidTank getTanks();
IConfigManager getConfigManager(); // still registers Settings.INTERFACE_TERMINAL, YesNo.YES, at construction
String getTermName();
long getSortValue();
DimensionalCoord getLocation();
IUpgradeableHost getHost();
int getPriority();  void setPriority(int newValue);
void writeToNBT(NBTTagCompound data);  void readFromNBT(NBTTagCompound data);
void saveChanges();
void notifyNeighbors();  void gridChanged();
AECableType getCableConnectionType(AEPartLocation dir);
boolean hasCapability(Capability<?> capabilityClass, EnumFacing facing);
<T> T getCapability(Capability<T> capabilityClass, EnumFacing facing);
// MEStorage surface, all delegating to getInventory():
long insert(AEKey what, long amount, Actionable mode, IActionSource source);
long extract(AEKey what, long amount, Actionable mode, IActionSource source);
void getAvailableStacks(KeyCounter out);
ITextComponent getDescription();
// IAEFluidInventory callback surface (onFluidInventoryChanged, 3 overloads) - unchanged shape, retyped
// AEFluidStack.fromFluidStack(added) -> GenericStack.fromFluidStack(added) at both call sites.
// IConfigurableFluidInventory.getFluidInventoryByName("config") -> IFluidHandler unchanged.
// IAEAppEngInventory.getInventoryByName("upgrades") -> IItemHandler unchanged.
```

**Collapsed by the new model, not cut:** pre-migration, `getInventory(IStorageChannel<T>)` served two
independent monitors — `items` (an `MEMonitorPassThrough`, always the full network, regardless of this
interface's own fluid config) and `fluids` (full network, or the config-gated `InterfaceInventory`, depending
on `hasConfig()`). `MEStorage` is not per-channel any more, so one object now has to answer for every key
type at once. The natural, `DualityInterface`-precedented replacement is a single `getInventory(): MEStorage`
that mirrors the *fluid* half exactly (config -> own tanks; no config -> whole network, any type) and, as a
consequence, now also serves items the way the old `items` monitor did whenever there is no config (full
network pass-through) — the one behavioural difference is that a config'd fluid interface's `InterfaceInventory`
(a thin `MEMonitorIFluidHandler` wrapper around the tanks) will report 0 for an item query instead of the old
model's outright `null` return for the items channel. No caller was found that distinguishes "no monitor at
all" from "a monitor that has nothing"; both mean "you can't get items here." Flagged per rule 6 as a shape
change forced by the new type-erased model, not a feature removal — the fluid interface's own storage/config
behaviour, `Settings.INTERFACE_TERMINAL`, `getTermName`/`getSortValue`/`getLocation`, and the `requireWork`
stocking loop are all unchanged.

**`getMonitorable(IActionSource)` keeps the fluid interface's own pre-migration semantics, not the item
interface's.** `DualityInterface.getMonitorable(src, myInterface)` falls back to a local-buffer
`InterfaceInventory` when access is denied (so a foreign interface's block capability can still push directly
into it even across grids) — but the pre-migration `DualityFluidInterface.getMonitorable(src)` returned `null`
on access denial, and the fluid interface never had `DualityInterface`'s cross-grid push-into-adjacent-buffer
mechanism (no `pushPattern`, no facing-based send queue) to begin with. Kept as `null` on denial; adopting the
item interface's broader fallback here would be adding a capability the fluid interface never had, not
"following the precedent."

**Fork-specific mechanics, verified preserved:** `Settings.INTERFACE_TERMINAL` (`DualityFluidInterface`
constructor, `YesNo.YES`) — still registered, still read every tick by the committed
`ContainerFluidInterfaceConfigurationTerminal`; the whole `FLUID_INTERFACE_CONFIGURATION_TERMINAL` mechanic
depends on `getTermName()`/`getSortValue()`/`getLocation()` staying live, which they do, byte-for-byte
unchanged logic (only `getTermName()`'s neighbor-scan uses `IFluidInterfaceHost` / `sameGrid`, unaffected by
the storage retype). `Upgrades.CAPACITY` — still read in `updatePlan`, `readFromNBT`, `onChangeInventory`
(upgrade slot changed) to recompute `tanks.setCapacity(...)`; formula unchanged
(`Math.pow(4, installedCapacityCards + 1) * Fluid.BUCKET_VOLUME`). `NUMBER_OF_TANKS = 9` and
`TANK_CAPACITY = Fluid.BUCKET_VOLUME * 4` — unchanged constants. `Upgrades.PATTERN_EXPANSION` — grep-confirmed
absent from the pre-migration file; the fluid interface has no crafting patterns or pattern slots in this
fork (upstream and AE2UD agree here), so there was nothing to preserve. `Settings.FUZZY_MODE`/`Settings.ACTIONS`
— grep-confirmed absent from the pre-migration file, nothing to preserve.

**§9.1 `.equals(` audit, all eight files:**
- `DualityFluidInterface.updatePlan()` — **one real hazard, fixed.** The old `req.equals(stored)` used
  `IAEFluidStack`'s size-insensitive `equals()` to mean "same fluid, don't care about the amount yet"; a
  literal `GenericStack.equals()` port would make a tank sitting at a *different level* than requested
  wrongly take the "different fluid, dispose everything" branch instead of the "top up/drain to the
  requested level" branch. Translated to `req.what().equals(stored.what())`.
- `AEFluidInventory.setFluidInSlot()` — **one real hazard, fixed.** The old `Objects.equals(this.fluids[slot],
  fluid)` relied on the same size-insensitive `equals()` to decide "same fluid, amount-only change" versus
  "different fluid, remove-and-replace" (which fire different callback parameter pairs — added-only versus
  added-and-removed). Translated to an explicit key-identity check (`current.what().equals(fluid.what())`)
  computed once and branched on, rather than comparing the `GenericStack`s themselves.
- `AEFluidInventory.fill(int, FluidStack, boolean)` / `drain(int, FluidStack, boolean)` — the old
  `fluid.equals(resource)` cross-type comparison (`IAEFluidStack.equals(FluidStack)`, also size-insensitive)
  meant "is this the fill/drain target the same fluid as what's already in the slot". Translated to
  `AEFluidKey.matches(fluid.what(), resource)`, the API's own size-insensitive fluid/FluidStack comparison —
  exactly the tool the hazard note recommends.
- `DualityFluidInterface.usePlan()` — no `.equals(` call remains: the old code's network-availability
  pre-check (`getStorageList().findPrecise(work) != null`) was dropped in favour of calling
  `Platform.poweredExtraction` directly and checking `acquired > 0`, mirroring
  `appeng.helpers.DualityInterface#usePlan`'s own precedent (it never pre-checked availability either,
  `MEStorage` has no `findPrecise`, and `poweredExtraction`/`poweredInsert` already simulate-check
  internally) — nothing to translate, the pre-check was a pure optimisation, not a behavioural gate.
- Everything else (`AEFluidTank`, `AENetworkFluidInventory`, `IFluidInterfaceHost`) — no `.equals(` calls
  found; `AENetworkFluidInventory.fill()`'s network-insert branch compares by `long` amount, not by stack.

**Anything that could not be preserved: none.** Every mechanic enumerated from the pre-migration read of all
eight files (the two-way tank/config reconciliation loop, the sticky-tank-capacity-upgrade recompute, the
interface-priority-gated `InterfaceInventory`, the `Settings.INTERFACE_TERMINAL` gate, the block-picking
`getTermName()` heuristic including its GT-machine special case) has a direct translation in the new model.

**Changed outside the assigned file list: none.** `appeng.container.implementations.
ContainerFluidInterfaceConfigurationTerminal`, `appeng.client.me.ClientDCInternalFluidInv`,
`appeng.parts.AEBasePart`, `appeng.fluids.tile.TileFluidInterface` and `appeng.me.storage.MEMonitorIFluidHandler`
were all read (per CONTRACT.md's "read them before writing, they are the specification" instruction and to
confirm `TileFluidInterface` — not listed under any wave-5 agent — has no deleted-type reference at all,
grep-confirmed, so it needed no change and none was made) but none was edited.

**API gaps hit: none.** Every method needed — `GenericStack.fromFluidStack`/`writeTag`/`readTag`,
`AEFluidKey.of`/`.matches`/`.toStack`/`.getFluid`, `MEStorage.getInventory()` (unified, no channel argument),
`NullInventory.of()`, `Platform.poweredInsert`/`poweredExtraction` (`MEStorage`-based 5-arg and 6-arg
overloads), `IStorageService.getInventory()` — was already in `src/api` or `appeng.util.Platform`/
`appeng.me.storage` from earlier waves, exactly as CONTRACT.md's wave-5-prerequisites section described.
`src/api` was not touched.

### Wave 5c — appeng.fluids.container (done)

Six files: `ContainerFluidConfigurable`, `ContainerFluidFormationPlane`, `ContainerFluidInterface`,
`ContainerFluidStorageBus`, `ContainerFluidTerminal`, `ContainerMEPortableFluidCell`. `IFluidSyncContainer` and
`container/slots/IMEFluidSlot` were already migrated by hand before the wave (CONTRACT.md's "Wave 5
prerequisites") and were read, not edited.

```java
// appeng.fluids.container.ContainerFluidConfigurable implements IFluidSyncContainer   (abstract base)
public abstract IAEFluidTank getFluidConfigInventory();                 // return type unchanged, see prerequisites
protected boolean isValidForConfig(int slot, GenericStack fs);          // was IAEFluidStack
public void receiveFluidSlots(Map<Integer, GenericStack> fluids);       // was Map<Integer, IAEFluidStack>
// transferStackToContainer/standardDetectAndSendChanges: GenericStack.fromFluidStack(fs) replaces the deleted
// AEFluidStack.fromFluidStack(fs); everything else (empty-slot scan, "clear config slots invalidated by a
// removed capacity upgrade" sweep, FluidSyncHelper wiring) untouched.

// appeng.fluids.container.ContainerFluidFormationPlane extends ContainerFluidConfigurable
protected boolean isValidForConfig(int slot, GenericStack fs);          // was IAEFluidStack; body untouched,
// param type only - the override never read the stack itself, only the slot index.

// appeng.fluids.container.ContainerFluidStorageBus extends ContainerFluidConfigurable
protected boolean isValidForConfig(int slot, GenericStack fs);          // was IAEFluidStack
// partition() rewritten around PartFluidStorageBus#getInternalHandler() - see the cross-agent note below.
// clear()/getFluidConfigInventory() unchanged in shape.

// appeng.fluids.container.ContainerFluidInterface extends ContainerFluidConfigurable implements IConfigManagerHost
public void receiveFluidSlots(Map<Integer, GenericStack> fluids);       // was Map<Integer, IAEFluidStack>
public void setTargetStack(@Nullable AEKey stack);                     // was IAEFluidStack, pinned by
                                                                        // PacketTargetFluidStack
// doAction(FILL_ITEM/EMPTY_ITEM): clientRequestedTargetFluid retyped AEKey; fh.fill/drain calls now build their
// FluidStack via `((AEFluidKey) clientRequestedTargetFluid).toStack(amount)` (pattern-matched) instead of
// mutating a shared IAEFluidStack's stackSize field.

// appeng.fluids.container.ContainerFluidTerminal extends AEBaseContainer
//         implements IConfigManagerHost, IConfigurableObject, IStorageWatcherNode   (was IMEMonitorHandlerReceiver<IAEFluidStack>)
public void setTargetStack(@Nullable AEKey stack);                     // was IAEFluidStack
public void onStackChange(AEKey what, long amount);                   // new, case 1 (see below)
public void updateWatcher(IStackWatcher newWatcher);                  // new, documented no-op (see below)
// monitor retyped IMEMonitor<IAEFluidStack> -> MEStorage; terminal.getInventory(channel) -> terminal.getInventory().
// The old `IItemList<IAEFluidStack> fluids` bookkeeping field is gone, replaced by the case-1/case-2 split below.
// transferStackInSlot/doAction: Platform.poweredInsert/poweredExtraction's `long` (amount moved) return value
// replaces the old "returns the leftover IAEFluidStack" contract; this.monitor.insert(...) replaces
// this.monitor.injectItems(...) for the raw (non-powered) fallback insert. No GridInventoryEntry sent from this
// class ever sets craftable=true or a nonzero requestableAmount - the pre-migration file never surfaced either
// for fluids (checked before rewriting), so none was added.

// appeng.fluids.container.ContainerMEPortableFluidCell extends AEBaseContainer
//         implements IAEAppEngInventory, IConfigManagerHost, IConfigurableObject, IUpgradeableCellContainer, IInventorySlotAware
//         (was ...,  IMEMonitorHandlerReceiver<IAEFluidStack>)
public void setTargetStack(@Nullable AEKey stack);                     // was IAEFluidStack, pinned by
                                                                        // PacketTargetFluidStack
// monitor retyped IMEMonitor<IAEFluidStack> -> MEStorage; terminal.getInventory(channel) -> terminal.getInventory().
// postChange/onListUpdate/isValid (the old IMEMonitorHandlerReceiver trio) are gone - no interface left to
// implement them for; replaced by the case-2 collectChanges()/queueInventory() pair (see below).
// transferStackInSlot/doAction translated the same way as ContainerFluidTerminal's.
// appeng.fluids.container.ContainerWirelessFluidTerminal (not in this wave's file list - a trivial
// ContainerMEPortableFluidCell subclass with no old-model reference of its own, confirmed by reading it) needed
// no edit and inherits setTargetStack/postUpdate-adjacent behaviour unchanged.
```

**Terminal live updates (CONTRACT.md §10 "Third case"), which case each terminal landed in:**

- **`ContainerFluidTerminal` — case 1 (real push) when its host is `AbstractPartTerminal`, case 2 otherwise.**
  The constructor mirrors `ContainerMEMonitorable`'s branch exactly: `terminal instanceof AbstractPartTerminal`
  sets `networkTerminalPart` and calls `addTerminalListener(this)`; `onStackChange(AEKey, long)` buffers into
  `pendingPushChanges`, drained every tick in `collectChanges()`. In practice this container is only ever
  constructed with `appeng.fluids.parts.PartFluidTerminal` (5-A's file), which extends `AbstractPartTerminal` -
  confirmed by reading it - so it always gets case 1 today; the case-2 branch exists for symmetry with
  `ContainerMEMonitorable` and so an addon's own non-`AbstractPartTerminal` `ITerminalHost` still gets a working
  fallback rather than an NPE. `removeListener`/`onContainerClosed` call `networkTerminalPart.removeTerminalListener(this)`,
  same lifecycle as the item-side terminal.
- **`ContainerMEPortableFluidCell` — case 2 unconditionally.** Its host is always an `IPortableCell`
  (`WirelessTerminalGuiObject` in every call site read), which is never an `AbstractPartTerminal` - a portable
  item GUI object has no grid node of its own to hang a watcher off. `collectChanges()` snapshots
  `MEStorage.getAvailableStacks()` (a **fresh** `KeyCounter` per the API contract, safe to store as
  `previousAvailableStacks` - never `IStorageService.getCachedInventory()`'s mutable one, per the STATUS.md
  warning) and diffs it against the previous tick's snapshot, exactly upstream's `MEStorageMenu.broadcastChanges()`.
  No `IStorageWatcherNode` implementation was added here, since there is nothing for it to be registered against.
- Both terminals send deltas as `GridInventoryEntry`-wrapped `PacketMEFluidInventoryUpdate` packets (kept as its
  own class, not merged into `PacketMEInventoryUpdate`, per the wave 4 prerequisite - that merge is still not
  this wave's decision to make). Neither case needed an addition to the frozen API.

**Fork-specific mechanics from point 6, checked file by file:**

- **`Settings.STICKY_MODE`/`Upgrades.STICKY` on `ContainerFluidStorageBus`.** Checked against the pre-port tree
  at `1e855f729` before writing anything: unlike the item-side `ContainerStorageBus`/`PartStorageBus`, the fluid
  storage bus **never** registered `Settings.STICKY_MODE` on its config manager and its container never had a
  `stickyMode` field, before or after this port - the sticky *mechanic itself* (`Upgrades.STICKY` ->
  `handler.setSticky(true)`) already lives in `appeng.fluids.parts.PartFluidStorageBus` (5-A's file, line 446-447
  in the pre-migration tree) and in `appeng.me.storage.NetworkStorage`/`MEInventoryHandler` (wave 1b), neither of
  which this wave touches. There is nothing to restore here specifically because there was never a GUI-facing
  `stickyMode` setting on the fluid bus to lose - confirmed by diffing against the pre-port revision, not
  assumed. Flagged here per the brief's explicit instruction to report on this exact mechanic rather than
  silently deciding either way.
- **`Settings.STORAGE_FILTER`, `Settings.ACCESS`, `Settings.FUZZY_MODE`, `Upgrades.CAPACITY` on
  `ContainerFluidStorageBus`.** All untouched `@GuiSync` fields / `getUpgradeable().getConfigManager()` reads;
  only `partition()`'s cell-scanning internals and the `isValidForConfig` parameter type changed.
  `Upgrades.INVERTER` - grep-confirmed absent from the pre-migration `ContainerFluidStorageBus` (it is read by
  `PartFluidStorageBus` itself, not surfaced in this container), nothing to preserve here.
- **`Settings.SORT_BY`/`SORT_DIRECTION`/`VIEW_MODE` on `ContainerFluidTerminal`/`ContainerMEPortableFluidCell`.**
  Both containers' `clientCM.registerSetting(...)` calls are untouched.
- **`Settings.CRAFT_ONLY`, `Settings.SCHEDULING_MODE`.** Grep-confirmed absent from every file in this wave's
  list - those settings surface in `ContainerFluidConfigurable`'s only other subclass in the tree
  (`ContainerFluidFormationPlane`) not at all, and in the export-bus-only branch of the item-side
  `ContainerUpgradeable.loadSettingsFromHost` (`instanceof PartExportBus`), which no fluid container reaches.
  Nothing to preserve.
- **The `FLUID_*` part family.** `ContainerFluidTerminal`/`ContainerFluidInterface`/`ContainerFluidStorageBus`/
  `ContainerFluidFormationPlane`/`ContainerMEPortableFluidCell` all still exist with their pre-port constructor
  shapes untouched (no signature was changed on any of them) - upstream has no equivalent of any of these and is
  not a guide for whether they should exist, per point 6.

**§9.1 `.equals(` audit, all six files:**

- `ContainerFluidInterface.setTargetStack`/`ContainerFluidTerminal.setTargetStack`/
  `ContainerMEPortableFluidCell.setTargetStack`: `stack.equals(this.clientRequestedTargetFluid)` - safe. All
  three compare bare `AEKey`s, never `GenericStack`s; `AEKey` carries no amount (only `GenericStack` does), so
  this is already the size-insensitive identity check the old `FluidStack#isFluidEqual` was. Same conclusion
  wave 4c reached for the item-side `AEBaseContainer.setTargetStack`.
- No other `.equals(` call on a stack-shaped value exists in any of the six files - grep-confirmed. Every other
  identity check translated to `instanceof AEFluidKey`/`AEItemKey` pattern matches (e.g. the FILL_ITEM/EMPTY_ITEM
  branches' `this.clientRequestedTargetFluid instanceof AEFluidKey targetFluid`) or `KeyCounter`/
  `Object2LongMap.Entry<AEKey>` key lookups, never a whole-`GenericStack` or old `IAEFluidStack.equals()`-style
  comparison.
- **No whole-`GenericStack` comparison exists anywhere in this wave's six files.**

**Client/server boundary changes, called out explicitly per the brief:** none beyond what CONTRACT.md's wave 4
prerequisites and wave 5 prerequisites already pinned before this wave started. `IFluidSyncContainer.
receiveFluidSlots(Map<Integer, GenericStack>)`, `ContainerFluid*.setTargetStack(AEKey)` and the
`PacketMEFluidInventoryUpdate`/`GridInventoryEntry` wire format were all fixed by already-committed code (wave
4a's packets) or the by-hand prerequisites before this wave touched anything - this wave implemented against
those shapes, it did not invent a new wire format. Verified against 5-D's already-migrated
`GuiFluidTerminal`/`GuiMEPortableFluidCell` (read, not edited): both call `this.container.setTargetStack(stack ==
null ? null : stack.what())` and `this.container.isPowered()` against exactly the signatures produced here, and
their own `postUpdate(List<GridInventoryEntry>)` lives on the GUI classes themselves (`PacketMEFluidInventoryUpdate`
dispatches to the client screen, not the container - confirmed by reading the packet), so no method of that name
was added to either container in this wave.

**Cross-agent assumption flagged (5-A's file, not yet landed at the time of this writing):**
`ContainerFluidStorageBus.partition()` calls `this.storageBus.getInternalHandler()` (`PartFluidStorageBus`,
5-A's file) and assigns the result to a local `MEStorage` variable, then calls `.getAvailableStacks()` on it.
This is written against the item-side precedent (`appeng.parts.misc.PartStorageBus#getInternalHandler():
MEInventoryHandler`, a `MEStorage`) rather than against 5-A's actual migrated code, which had not landed when
this file was written. Nothing in CONTRACT.md's wave-5-prerequisites section pins `PartFluidStorageBus`'s
post-migration signature, so this is an assumption, not a confirmed shape - if 5-A's `getInternalHandler()`
returns something that is not `MEStorage`-shaped, or is renamed, this call site needs a follow-up fix. Flagged
per the brief's instruction to call out cross-agent seams explicitly rather than silently assume they resolve.

**Mechanics that could not be preserved: none.** Every mechanic enumerated from a pre-migration read of all six
files (the fluid config-slot capacity-upgrade gating, the FILL_ITEM/EMPTY_ITEM item-drain/-fill dance in both
terminals and the fluid interface, the storage-bus partition/clear buttons, the formation-plane's per-row
capacity gate) has a direct translation in the new model.

**Files touched outside the assigned list: none.** `IFluidSyncContainer` and `container/slots/IMEFluidSlot` were
read, per the brief's instruction, and confirmed already in their final shape - neither was edited.
`appeng.container.implementations.ContainerMEMonitorable` (item-side precedent for both terminals),
`appeng.container.implementations.ContainerStorageBus` (item-side precedent for the storage bus),
`appeng.fluids.helper.FluidSyncHelper`, `appeng.fluids.util.IAEFluidTank`,
`appeng.core.sync.packets.{PacketFluidSlot,PacketTargetFluidStack,PacketMEFluidInventoryUpdate,PacketInventoryAction}`,
and `appeng.fluids.client.gui.{GuiFluidTerminal,GuiMEPortableFluidCell,GuiWirelessFluidTerminal}` were all read as
the specification for this wave's call sites and callers, per rule 3/8 of the brief, but none was edited.

**API gaps hit: none.** Every method needed - `GenericStack.fromFluidStack`, `AEFluidKey.of(FluidStack)`/
`.toStack(int)`, `AEKey.equals`, `MEStorage.insert`/`.extract`/`.getAvailableStacks`, `KeyCounter.get`/`.iterator`,
`Platform.poweredInsert`/`poweredExtraction` (5-arg and 6-arg `MEStorage`-based overloads),
`ITerminalHost.getInventory()` (no-channel form), `IStorageWatcherNode`/`IStackWatcher`,
`AbstractPartTerminal.addTerminalListener`/`removeTerminalListener`, `GridInventoryEntry`,
`PacketMEFluidInventoryUpdate.appendFluid`, `PacketTargetFluidStack(AEKey)` - was already in `src/api`, already
committed by wave 4, or already migrated by hand per the wave 5 prerequisites. `src/api` was not touched.

**Mandatory deleted-symbol scan, run over `appeng.fluids.container` after finishing:** prints nothing.

### Wave 5a — appeng.fluids.parts, items, registries (done)

Eleven files: `FluidHandlerAdapter`, `PartFluidAnnihilationPlane`, `PartFluidExportBus`, `PartFluidFormationPlane`,
`PartFluidImportBus`, `PartFluidInterface`, `PartFluidLevelEmitter`, `PartFluidStorageBus`, `PartSharedFluidBus`
(`appeng.fluids.parts`); `BasicFluidCellGuiHandler` (`appeng.fluids.registries`); `BasicFluidStorageCell`
(`appeng.fluids.items`). Plus the two registration edits (`appeng.parts.automation.InitStackWorldBehaviors`,
`appeng.parts.misc.InitExternalStorageStrategies`) that this wave exists for. `appeng.fluids.parts.PartFluidTerminal`
— present in the package but not on this wave's file list — was read and confirmed to already extend the
already-migrated `AbstractPartTerminal` with no reference to any deleted type; it needed no change and none was
made (consistent with 5-C's Wave 5c report, which reads it as already-correct).

Five new classes were created in `appeng.fluids.parts`, mirroring the item-side strategy layer
(`appeng.parts.automation.StorageImportStrategy`/`StorageExportStrategy`/`ItemPickupStrategy`/
`ItemPlacementStrategy`/`appeng.parts.misc.ItemHandlerAdapter.Strategy`) in shape. Unlike their item-side
counterparts they are **public** (classes, constructors and static factory methods), because
`InitStackWorldBehaviors`/`InitExternalStorageStrategies` — the classes that register them — live in a different
package (`appeng.parts.automation`/`appeng.parts.misc`), whereas the item strategies share a package with their
registrar and can stay package-private.

```java
// appeng.fluids.parts.FluidTransferContext implements StackTransferContext   — package-private
// Fluid-bus concrete StackTransferContext, mirroring appeng.parts.automation.StackTransferContextImpl (which is
// package-private there and so cannot be reused from this package). Same non-frozen extras for the same reason:
FluidTransferContext(MEStorage internalStorage, IEnergySource energySource, IActionSource actionSource,
        int operationsRemaining, IPartitionList filter, @Nullable FuzzyMode fuzzyMode);
boolean hasDoneWork();  IPartitionList getPartitionList();  @Nullable FuzzyMode getFuzzyMode();  IEnergySource getEnergySource();

// appeng.fluids.parts.FluidImportStrategy implements StackImportStrategy   — public
public static StackImportStrategy createFluid(World world, BlockPos fromPos, EnumFacing fromSide);
// One bounded drain-then-insert per call (up to the whole per-tick millibucket budget), not a discrete-chunk
// loop: the pre-port PartFluidImportBus#doBusWork always did a single, bounded drain per tick, fluids were
// never chunked into "operations" the way item stacks are. Supports Upgrades.FUZZY (AEKey#fuzzyEquals against
// the configured partition list) and an empty-filter "accept anything" mode, matching the item strategy's shape
// — neither existed in the pre-port fluid import bus, which only ever did an exact IAEFluidStack#equals(FluidStack)
// check; see the report below for why wiring this up for real was judged in-scope.

// appeng.fluids.parts.FluidExportStrategy implements StackExportStrategy   — public
public static StackExportStrategy createFluid(World world, BlockPos fromPos, EnumFacing fromSide);
// simulate-extract -> simulate-fill -> extract -> fill sequence, mirroring the pre-port
// PartFluidExportBus#doBusWork's simulate/fill/extract dance one for one.

// appeng.fluids.parts.FluidPlacementStrategy implements PlacementStrategy   — public
public FluidPlacementStrategy(World world, BlockPos pos, EnumFacing fromSide, TileEntity host, @Nullable UUID ownerUuid);
// Full-bucket-only placement via FluidUtil.tryPlaceFluid, ported from PartFluidFormationPlane#injectItems.
// No "place as entity" branch exists (fluids have no entity form), matching the pre-port class exactly.

// appeng.fluids.parts.FluidPickupStrategy implements PickupStrategy   — public
public FluidPickupStrategy(World world, BlockPos pos, EnumFacing side, TileEntity host,
        Map<Enchantment, Integer> enchantments, @Nullable UUID ownerUuid);
// Drains an adjacent fluid source block into the PickupSink, ported from
// PartFluidAnnihilationPlane#pickupFluid/#storeFluid. canPickUpEntity/pickUpEntity always return false/no-op —
// fluids have no entity form in this fork, matching the pre-port class (which never overrode onEntityCollision).
// Still sends the pre-port's PacketTransitionEffect(..., true) visual on a successful drain (reconstructing the
// plane's own AEPartLocation from the constructor's fromSide), which the shared base's tick path does not send
// on its own — see the report's mechanic-preservation notes.

// appeng.fluids.parts.FluidHandlerAdapter implements MEStorage, ITickingMonitor   — public (was package-private
//         on the item side; see above for why)
// Fluid counterpart of appeng.parts.misc.ItemHandlerAdapter, identical shape: a KeyCounter cache rebuilt on
// construction, after every real insert/extract, and once per onTick().
public static final class Strategy implements ExternalStorageStrategy {
    public Strategy(World world, BlockPos fromPos, EnumFacing fromSide);
    // createWrapper(extractableOnly, callback) re-resolves CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY fresh
    // every call, exactly like ItemHandlerAdapter.Strategy. This is the ExternalStorageStrategy for
    // AEKeyType.fluids(), registered by InitExternalStorageStrategies.
}
```

**The two registration edits — the point of the wave:**

```java
// appeng.parts.automation.InitStackWorldBehaviors.register()   — added, alongside the existing items() lines
StackImportStrategy.register(AEKeyType.fluids(), FluidImportStrategy::createFluid);
StackExportStrategy.register(AEKeyType.fluids(), FluidExportStrategy::createFluid);
PlacementStrategy.register(AEKeyType.fluids(), FluidPlacementStrategy::new);
PickupStrategy.register(AEKeyType.fluids(), FluidPickupStrategy::new);

// appeng.parts.misc.InitExternalStorageStrategies.register()   — added, alongside the existing items() line
ExternalStorageStrategy.register(AEKeyType.fluids(), FluidHandlerAdapter.Strategy::new);
```

With these in place the already-migrated, already-committed `appeng.parts.automation.PartImportBus`,
`PartExportBus`, `PartAnnihilationPlane`, `PartAbstractFormationPlane` and `appeng.parts.misc.PartStorageBus`
all serve fluids with no further change to those files (none was made) — a fluid-wrapped stack dropped into any
of their generic, item-shaped config slots is handled correctly by the strategies above, and
`appeng.parts.misc.PartStorageBus.CompositeExternalStorage` now actually composes two types (items and fluids)
for the first time.

**The dedicated fluid parts still exist, split from the generic ones, and route through the same registry.**
AE2UD keeps separate Fluid Import/Export/Storage Bus, Fluid Formation Plane and Fluid Annihilation Plane
parts/items (own textures, own GUIs) rather than merging into the generic item-shaped parts — the same
"split, not merged" shape CONTRACT.md §5/§10 already calls out for formation and annihilation planes, extended
here to the busses too, since AE2UD never had a single universal bus item. Each dedicated fluid part obtains its
strategy through `appeng.api.behaviors.StackWorldBehaviors`, the exact same public entry point an addon would
use — no bypass, no privileged access:

- `PartFluidImportBus`/`PartFluidExportBus` extend the (rewritten) `PartSharedFluidBus`, whose `AEFluidInventory`
  config is unchanged in shape from the pre-port class (a fluid-tank, not the item bus'
  `AppEngInternalAEInventory`) — there is no frozen base class here forcing an item-shaped config, unlike the
  formation plane below.
- `PartFluidAnnihilationPlane` now **extends the generic, already-migrated `appeng.parts.automation.
  PartAnnihilationPlane`** instead of duplicating its whole box/connection/tick/entity-collision machinery by
  extending `PartBasicState` directly (the pre-port shape). It overrides only `createPickupStrategies(...)` to
  substitute `FluidPickupStrategy` for the generic list — the exact pattern
  `appeng.parts.automation.PartIdentityAnnihilationPlane` already established for its own silk-touch substitution.
  This deleted roughly 200 lines of duplicated logic that is now shared, and was the biggest simplification of
  this wave.
- `PartFluidFormationPlane` extends the (already-committed, non-generic) `PartAbstractFormationPlane`,
  implementing `getKeyType() = AEKeyType.fluids()` and `insert(...)` with the pre-port block-fill logic moved
  into `FluidPlacementStrategy`. Its `getConfigInventory()` shape required a real design decision — see below.
- `PartFluidStorageBus` mirrors `appeng.parts.misc.PartStorageBus`'s resolution order (direct
  `IStorageMonitorableAccessor` link, then the generic `ExternalStorageStrategy` registry) and stable-handler
  shape, restricted to `AEKeyType.fluids()` alone. A direct sub-network link is wrapped in a private
  `FluidOnlyStorage` decorator so this bus never leaks item access even when linked to another network that
  carries both — the new-model equivalent of the pre-port `getInventoryWrapper`'s
  `inventory.getInventory(IFluidStorageChannel.class)` call under the old per-channel API.

**`PartFluidFormationPlane`'s config: a real cross-agent conflict, found and resolved, not assumed away.**
`PartAbstractFormationPlane#getConfigInventory()` (wave 3a, already committed, frozen) returns the concrete class
`appeng.tile.inventory.AppEngInternalAEInventory` — not an interface. Reading that class (wave 2, already
committed) shows its `IItemHandler` mutation surface (`insertItem`/`setStackInSlot`) always builds slot content
via `GenericStack.fromItemStack`, which can only ever produce an **item**-typed key — so a plain
`AppEngInternalAEInventory` can never actually hold a fluid filter entry through normal play, no matter how its
slot's `ItemStack` is wrapped. Meanwhile `appeng.fluids.container.ContainerFluidFormationPlane` (5-C, already
committed, "done" before this was written) still calls `plane.getConfig(): IAEFluidTank` for its actual GUI,
matching every other fluid part's shape and giving no sign of expecting an item-wrapped config. Naively
switching this plane's real config to `AppEngInternalAEInventory` (as the wave 3a base class's own javadoc,
written before this gap was found, suggested was possible) would have done one of two bad things: broken 5-C's
container outright, or — if a second config were added without care — silently left `updateFilter()` (the
`final`, inherited filter-builder) always seeing an empty configured-fluids list, turning "restrict what this
plane places" into a permanent no-op. That is exactly the class of regression rule 6 forbids, and it would have
been invisible without reading both the frozen base class and 5-C's already-committed container side by side.
**Resolution:** the plane keeps two inventories. `config` (an `AEFluidInventory`, unchanged shape from the
pre-port class) is the real, GUI-facing 63-slot filter, still returned by `getConfig()` for 5-C's container.
`filterView` (an `AppEngInternalAEInventory`, never exposed to any GUI or capability) exists purely so
`getConfigInventory()` has something to return; it is kept in sync with `config` by `syncFilterView()`, which
round-trips through `GenericStack.writeTag`/`AppEngInternalAEInventory#readFromNBT` — both already generic over
any `AEKey` type, unlike the `IItemHandler` surface — rather than through `insertItem`/`setStackInSlot`. This
preserves the pre-port fluid-filtering mechanic exactly, keeps 5-C's container working unchanged, and touches
neither frozen file. Flagged here in full per rule 6 and per the instruction to check cross-agent seams
explicitly rather than assume they resolve.

**Fork-specific mechanics, and where they now live:**

- **`Settings.SCHEDULING_MODE`** (`PartFluidImportBus`/`PartFluidExportBus`, `SchedulingMode.DEFAULT`). Wired up
  for real on the **export** bus (`getStartingSlot`/`updateSchedulingMode`, mirroring the item export bus
  exactly) — the pre-port fluid export bus stopped at the first configured slot that accepted anything each
  tick, which is preserved; scheduling mode only changes which slot is tried first. Registered but functionally
  inert on the **import** bus, matching the pre-port class exactly: import never picked one configured slot to
  try, it tested the incoming fluid against the whole configured filter set at once, so there is no "slot order"
  for scheduling mode to affect.
- **`Upgrades.STICKY`** — `PartFluidStorageBus.getInternalHandler()` calls
  `this.handler.setSticky(this.getInstalledUpgrades(Upgrades.STICKY) > 0)`, mirroring
  `appeng.parts.misc.PartStorageBus` exactly (both ultimately feed the same
  `appeng.me.storage.NetworkStorage#insert` sticky pass from wave 1b). Confirmed present at the same relative
  spot as the pre-migration file (`PartFluidStorageBus:446-448` in the pre-port tree).
- **`Settings.STORAGE_FILTER`** (`StorageFilter.EXTRACTABLE_ONLY`) — registered and read by
  `PartFluidStorageBus.getInternalHandler()` exactly as before (`extractableOnlyFilter`/`extractableOnly` locals
  feeding `setExtractFiltering`/`findExternalStorage`).
- **`Upgrades.CAPACITY`** — `PartFluidStorageBus.createFilter()`/`PartFluidFormationPlane`'s inherited
  `getFilterSlotsInUse()` (default `18 + CAPACITY*9`, unchanged, not overridden since the pre-port fluid
  formation plane used the identical formula) both still read it.
- **`Upgrades.INVERTER`** — `PartFluidStorageBus.getInternalHandler()`'s whitelist/blacklist toggle, unchanged.
- **`Upgrades.FUZZY`/`Settings.FUZZY_MODE`** — read by `PartFluidStorageBus.createFilter()`,
  `PartFluidFormationPlane`'s inherited `updateFilter()`, and (newly wired up for real, see above)
  `FluidImportStrategy`. The pre-port fluid import bus registered `Settings.FUZZY_MODE` but never read
  `Upgrades.FUZZY` at all (grep-confirmed against the pre-port tree) — its filter check was a bare
  `IAEFluidStack#equals(FluidStack)`. Judged safe and worth wiring up for real rather than left dead, because
  (a) the brief's fork-mechanic list names `Upgrades.FUZZY`/`Settings.FUZZY_MODE` as things that "must keep
  working" across this agent's files, and (b) the fuzzy-aware filter check was already necessary machinery for
  `FluidImportStrategy` to serve the registered-strategy role the wave exists to create, so extending it to the
  bus that already carries the setting was near-zero additional cost. **Not** extended to `FluidExportStrategy`
  itself (which fuzzy-searches nothing — it only ever receives one already-resolved `AEKey` per call, same as
  the item export strategy); export-side fuzzy *filter matching* stayed exactly as inert as the pre-port class,
  since broadening it into a fuzzy-search-across-the-network loop (mirroring `PartExportBus`'s
  `storageService.getCachedInventory().findFuzzy(...)` loop) is new functionality, not a preserved mechanic, and
  was left for the owner to request rather than added silently.
- **`Settings.CRAFT_ONLY`** — registered on both fluid busses for GUI parity with the pre-port class, but with
  no craft-on-demand behind it, exactly as before: the pre-port `PartFluidExportBus` never implemented
  `ICraftingRequester`/`MultiCraftingTracker` (grep-confirmed absent), unlike the item export bus. Building real
  craft-on-demand for fluids would be new functionality requiring its own design (autocrafting is
  fluid-agnostic today per CONTRACT.md §4.4's crafting-alignment deferral), not something this migration wave
  invents unasked. Stated plainly per rule 6 rather than silently left to look finished.
- **`Settings.ACCESS`/`Settings.REDSTONE_CONTROLLED`** — unchanged shape/read sites throughout.
- **`Settings.ACTIONS`** — grep-confirmed absent from every file in this wave's list, before and after; it is a
  GUI-button-only pseudo-setting read by `appeng.client`/`appeng.fluids.client` (5-D's territory), never
  registered or read by any part class in either the item or fluid tree.
- **The split item/fluid formation and annihilation planes** — both stay split, per CONTRACT.md §5/§10, and per
  the pre-port shape; see above for how each now shares its generic counterpart's machinery instead of
  duplicating it.
- **The fluid storage bus staying a separate part** despite `appeng.parts.misc.PartStorageBus` now being able to
  serve fluids on its own (see the registration note above) — preserved because AE2UD never had a single
  universal storage bus item to begin with, and removing the dedicated fluid one would be removing a mechanic
  (a distinct placeable item with its own recipe/texture/GUI), not merely changing its internals.

**Power charging: a real asymmetry, preserved rather than "fixed."** The item import/export busses charge AE
power for network access via `Platform.poweredInsert`/`poweredExtraction` (`StorageImportStrategy`/
`StorageExportStrategy`). The pre-port fluid import/export busses never did this at all — `FluidImportStrategy`/
`FluidExportStrategy` call `MEStorage#insert`/`#extract` directly, with no power deduction, exactly matching the
pre-port `IMEMonitor#injectItems`/`#extractItems` calls they replace. This looks like an oversight in the
original fork, but "fix" was not this wave's call to make silently; preserved byte-for-byte instead, flagged
here so the owner can decide if it is intentional.

**§9.1 `.equals(` audit, all eleven files (plus the five new strategy classes):** grepped every file.
`name.equals("config")` (four call sites, `PartSharedFluidBus`/`PartFluidStorageBus`/`PartFluidFormationPlane`/
`PartFluidLevelEmitter`) and `pos.offset(...).equals(neighbor)` (`PartFluidStorageBus`, vanilla `BlockPos`) are
unrelated to the hazard. One stack-identity check found:
`PartFluidLevelEmitter.onStackChange`'s `what.equals(myStack)` — safe, both sides are bare `AEKey`s (never
`GenericStack`s), and `AEKey` carries no amount (only `GenericStack` does), the same conclusion wave 4c reached
for `AEBaseContainer.setTargetStack` and wave 5c reached for the fluid containers' own `setTargetStack`.
**No whole-`GenericStack` comparison anywhere in this wave's files.**

**Mechanics that could not be preserved: none** beyond the two explicitly flagged above (fluid craft-on-demand
never existed to preserve; fluid busses' lack of power-charging is a pre-existing asymmetry, not this wave's
regression). Every other mechanic enumerated from a pre-migration read of all eleven files — the Sticky Card,
ACCESS/STORAGE_FILTER/FUZZY_MODE, Upgrades.INVERTER/CAPACITY/FUZZY, the split formation/annihilation planes, the
fluid annihilation plane's transition-effect packet, the fluid formation plane's full-bucket-only placement gate
— has a direct translation in the new model, verified above.

**Files touched outside the eleven-file list: none**, beyond the two registration files the brief names
explicitly (`appeng.parts.automation.InitStackWorldBehaviors`, `appeng.parts.misc.InitExternalStorageStrategies`).
`appeng.parts.automation.PartAnnihilationPlane`, `PartAbstractFormationPlane`, `PartImportBus`, `PartExportBus`,
`appeng.parts.misc.PartStorageBus`, `appeng.parts.misc.ItemHandlerAdapter`, `appeng.items.storage.
AbstractStorageCell`/`BasicItemStorageCell`, `appeng.tile.inventory.AppEngInternalAEInventory`,
`appeng.fluids.container.{ContainerFluidStorageBus,ContainerFluidFormationPlane,ContainerFluidLevelEmitter}` and
`appeng.fluids.parts.PartFluidTerminal` were all read as the specification for this wave's shapes and call
sites, per the brief's instruction, but none was edited.

**API gaps hit: none.** Every method needed — `AEKeyType.fluids()`/`.getAmountPerOperation()`,
`AEFluidKey.of(FluidStack)`/`.matches`/`.toStack`/`fuzzyEquals` (inherited from `AEKey`), `MEStorage.insert`/
`.extract`/`.getAvailableStacks`, `KeyCounter`, `IPartitionList.builder()`/`.getItems()`/`.isListed`,
`StackWorldBehaviors.createImportStrategies` (with its `Predicate<AEKeyType>` filter)/`createExportStrategies`/
`createExternalStorageStrategies`/`createPlacementStrategies`, `IStorageMonitorableAccessor`,
`IStorageProvider.requestUpdate`, `StorageCells.addCellGuiHandler` — was already in `src/api`, already committed
by earlier waves, or already migrated by hand per the wave 5 prerequisites. `src/api` was not touched; verified
with `gradlew compileApiJava` after finishing (green, as it was before this wave started).

**Mandatory deleted-symbol scan, run over `appeng.fluids.parts`, `appeng.fluids.items` and
`appeng.fluids.registries` after finishing:** prints nothing.

### Wave 6 — `appeng.integration.modules`, the HEI swap, and the first green build (done)

Seven files, no agents — the wave was small enough to do by hand, and the build was the actual work.

**The five easy ones lost a branch rather than gaining one.** `theoneprobe.part.StorageMonitorInfoProvider`,
`waila.part.StorageMonitorWailaDataProvider`, `theoneprobe.tile.CraftingMonitorInfoProvider` and
`waila.tile.CraftingMonitorWailaDataProvider` all carried a `// TODO: generalize` over an
`instanceof IAEItemStack … else if instanceof IAEFluidStack` pair. `IPartStorageMonitor.getDisplayed()` and
`TileCraftingMonitorTile.getJobProgress()` now return `GenericStack`, and every key type answers
`getDisplayName()` for itself, so both branches collapse into `displayed.what().getDisplayName()`. A monitor
showing a key type this fork has never heard of names it correctly with no change to these files. Where the
probe has to draw an item it uses `wrapForDisplayOrFilter()`, but takes the *name* from the key, so a fluid
job reads as the fluid and not as the placeholder item.

`bogosorter.InventoryBogoSortModule.COMPARATOR` is now
`Comparator<Object2LongMap.Entry<AEKey>>`, the same shape as everything in `appeng.util.ItemSorters`, and hands
bogosorter `AEKey.wrapForDisplayOrFilter()`. Keys of a non-item type all look alike to bogosorter and compare
equal; the terminal's sort is stable, so they keep their relative order rather than being shuffled. Ordering
them properly is bogosorter's call, not ours — it has no notion of a fluid.

**`appeng.integration.modules.jei.AvailableItems` is new, and `KeyCounter` deliberately does not replace it.**
`CraftableCallBack` and `JEIMissingItem` shared an `IItemList<IAEItemStack>` for "what can the terminal
supply". A `KeyCounter` cannot stand in, for two reasons that both matter here:

- it stores no craftable flag — keys carry none either (§8.3), which is why `GridInventoryEntry` exists;
- `add`/`set` treat an amount of zero as absence and drop the key, while **an entry with amount zero and
  craftable true is exactly what paints an ingredient slot blue instead of red**.

`AvailableItems` keeps both fields per key and indexes by `AEKey.getPrimaryKey()`, so fuzzy lookup scans one
item's variants rather than the network. `used` *is* a plain amount count and did become a `KeyCounter`; the
old `usedStack == null || ext > usedStack` pair collapses to one comparison because `KeyCounter.get` answers 0
for an absent key.

**Two additions outside the file list, both forced by where the data now lives.**
`ItemRepo.getAllEntries()` and `GuiMEMonitorable.getRepo()`. The public
`ContainerMEMonitorable.items` field is gone — the client-side listing lives in the screen's `ItemRepo`, which
is the only place the craftable flag survives the trip from the server. `AvailableItems.merge(container)` reads
it through `container.getGui()`, and contributes nothing rather than failing when no screen is attached.

**`build.gradle`** now pulls `mezz:jei:4.32.0` (HadEnoughItems) from `https://maven.cleanroommc.com`, which was
already a declared repository. HEI keeps JEI's mod id and the `mezz.jei` package, and publishes
RetroFuturaGradle obfuscation variants, so no `rfg.deobf` wrapper and not one import changed — including the
internal `mezz.jei.gui.*` classes `JEIMissingItem` reaches into.

**What the first real compile cost: 26 errors, none of them in wave 6's files.** They are listed in
`STATUS.md` under "What the first green build cost". `gradlew build` is green, tests included, and the
reobfuscated jar builds.

## 9.1 Standing hazard: `GenericStack.equals()` is not `IAEItemStack.equals()`

**Every remaining wave must check this.** The old `IAEItemStack.equals()` **ignored the stack size** — it meant "the same item". `GenericStack` is a record, so its `equals()` compares **the amount as well**.

A literal translation therefore compiles cleanly and silently changes behaviour. Wave 2 found a real instance: `CraftingTreeProcess.addProcess()` and `getTimes()` compared a *condensed* (summed) amount against a *per-slot* amount and relied on size-insensitive equality. Translated literally, any recipe using the same item in more than one slot would have quietly failed to match.

**Rule:** when the old code compared stacks for identity, compare `a.what().equals(b.what())`, not `a.equals(b)`. Only compare whole `GenericStack`s when the amount genuinely is part of the comparison. Audit every `.equals(` you carry over from a stack comparison, and say in your report which ones you checked.

Audited clean as of wave 2: `appeng.me`, `appeng.crafting`, `appeng.helpers`, `appeng.util`, `appeng.tile`, `appeng.core`.

Audited clean as of wave 3c: `appeng.items.storage`, `appeng.items.contents`, `appeng.recipes.game` (the six files in that entry) — no whole-`GenericStack` comparisons; all identity checks key off `AEKey`.

Audited clean as of wave 3d: `appeng.items.misc`, `appeng.items.tools.powered` (the four files rewritten in that entry) — one size-insensitive identity comparison found and translated to `AEItemKey.matches(ItemStack)` (`ToolColorApplicator.findNextColor`); no whole-`GenericStack` comparisons anywhere in this wave's files.

Audited clean as of wave 3b: `appeng.parts.misc`, `appeng.parts.reporting` (the nine files in that entry) — several size-insensitive identity comparisons found (`PartConversionMonitor.onPartActivate`'s wrench/fluid/item matches, `insertItem`'s inventory scan), all translated to `AEItemKey.matches(ItemStack)`/`AEFluidKey.matches(FluidStack)`; no whole-`GenericStack` comparisons anywhere in this wave's files.

Audited clean as of wave 4b: `ContainerCraftAmount`, `ContainerCraftConfirm`, `ContainerCraftingCPU`, `CraftingCPUStatus`, `ContainerPatternEncoder`, `ContainerWirelessPatternTerminal`, `ContainerNetworkStatus` (the seven files in that entry) — no whole-`GenericStack` comparisons and no carried-over `IAEItemStack.equals()`-style identity checks anywhere; the only `.equals(` calls found were `String.equals` and vanilla-`ItemStack` comparisons unrelated to the hazard.

Audited clean as of wave 4c: `AEBaseContainer`, `ContainerMEMonitorable`, `ContainerStorageBus`, `ContainerOreDictStorageBus`, `ContainerCellWorkbench`, `ContainerFluidInterfaceConfigurationTerminal`, `SlotCraftingTerm`, `SlotPatternTerm` (plus `AbstractPartTerminal`, touched outside the file list) — two `.equals(` calls on bare `AEKey`s found (`AEBaseContainer`/`ContainerFluidInterfaceConfigurationTerminal`'s `setTargetStack`), both confirmed safe because `AEKey` carries no amount (only `GenericStack` does, per the hazard's own definition); one size-insensitive identity check translated to `AEItemKey.matches(ItemStack)` (`AEBaseContainer.doAction`'s `ROLL_UP`/`PICKUP_SINGLE` branch); no whole-`GenericStack` comparisons anywhere in this wave's files.

Audited clean as of wave 4d: all 24 `appeng.client` files in that entry — one real instance found and fixed (`GuiFluidInterfaceConfigurationTerminal.matchedStacks`, switched from holding whole fluid stacks to bare `AEKey`s so a tank's amount drifting between search and redraw cannot make a membership check spuriously miss); `String.equals`/`AEKey.equals` hits elsewhere confirmed unrelated or already size-insensitive by definition; no whole-`GenericStack`/`GridInventoryEntry` comparison anywhere in this wave's files.

Audited clean (with fixes) as of wave 5b: `appeng.fluids.util` (`AEFluidInventory`, `AEFluidTank`,
`AENetworkFluidInventory`) and `appeng.fluids.helper` (`DualityFluidInterface`, `IFluidInterfaceHost`) — two
real size-insensitive identity comparisons found and fixed (`DualityFluidInterface.updatePlan`'s
`req.equals(stored)`, `AEFluidInventory.setFluidInSlot`'s `Objects.equals(this.fluids[slot], fluid)`), both
translated to key-only comparisons (`.what().equals(.what())`); two more `fluid.equals(resource)`-style
cross-type comparisons in `AEFluidInventory.fill`/`drain` translated to `AEFluidKey.matches(AEKey,
FluidStack)`; no whole-`GenericStack` comparisons anywhere in this wave's files.

Audited clean as of wave 5c: `appeng.fluids.container` (`ContainerFluidConfigurable`,
`ContainerFluidFormationPlane`, `ContainerFluidInterface`, `ContainerFluidStorageBus`, `ContainerFluidTerminal`,
`ContainerMEPortableFluidCell`) — three `.equals(` calls found, all on bare `AEKey`s in each terminal-shaped
container's `setTargetStack` (`ContainerFluidInterface`/`ContainerFluidTerminal`/`ContainerMEPortableFluidCell`),
all confirmed safe for the same reason wave 4c gave for `AEBaseContainer.setTargetStack`: `AEKey` carries no
amount, only `GenericStack` does. No whole-`GenericStack` comparison anywhere in this wave's six files.

## 9.1a Sibling hazard: `KeyCounter.reset()` is not `IItemList.resetStatus()`

Found by play-testing, not by any scan or review, and it is the same shape as §9.1: a method that translates
word for word and changes meaning.

`KeyCounter.reset()` zeroes the amounts but **keeps the keys**, and `KeyCounter.isEmpty()` counts **keys**.
The old `IItemList.isEmpty()` walked a *meaningful* iterator that skipped zero-size entries — the iterator
classes wave 0 deleted — so `resetStatus()` genuinely emptied the list.

`CraftingCPUCluster` carried the call over in three places. `waitingFor` was therefore never empty again,
`isBusy()` answered true forever, and **a finished crafting job never released its CPU**: no further job
could be submitted until the crafting storage block was broken and replaced. Nothing reported an error.

**Rule:** zeroing a counter and then asking whether it is empty means `clear()`. `reset()` is only for a
counter that the same scan is about to refill. `KeyCounter.reset()` documents this at the call site now.

Sites checked afterwards: `CraftingTreeNode.setSimulate` (benign — `used` is only read by `populatePlan`,
and `KeyCounter.add(key, 0)` is a no-op, so zeroed keys contribute nothing) and `JEIMissingItem.showError`
(correct — it wants the amounts zeroed and the keys kept, and reads through `get()`, which answers 0).

## 9.1b Sibling hazard: a strategy must never test its context's concrete type

Third of the same family, found by play-testing the fluids phase. `FluidImportStrategy` opened with
`if (!(context instanceof FluidTransferContext ctx) ...) return false;`, purely to reach two package-private
accessors. It worked on the legacy fluid bus — which built that class — and silently moved nothing on the
generic bus, which builds `StackTransferContextImpl`. No error, no log line, an import bus that just sits there.

The root cause was the duplicate: two byte-for-byte identical context classes in two packages, so the type test
compiled and looked meaningful. It is now one public class, and the test is impossible to write.

**Rule:** a `StackImportStrategy`/`StackExportStrategy`/`PlacementStrategy`/`PickupStrategy` sees only
`StackTransferContext`. Anything it needs belongs on that interface — `getFilter()` already covers filtering,
including fuzzy, which the `IPartitionList` bakes in. This is what makes an addon's key type work on our buses,
and ours work on an addon's: the whole point of the strategy layer (§3).

The same bug had a second half worth naming on its own: **the operation budget is in operations, not in the key
type's own units.** The strategy read `getOperationsRemaining()` as millibuckets. Convert through
`AEKey.getAmountPerOperation()` in both directions — `maxAmount = operations * factor` going in,
`reduceOperationsRemaining(max(1, moved / factor))` coming out, as `PartExportBus.exportOne` does.

## 9.1c Sibling hazard: a key's `equals` and `hashCode` must agree, and an empty tag is no tag

Fourth of the family, and the most expensive kind: `AEFluidKey.equals` compared `Fluid` by **identity** while
`hashCode` hashed `fluid.getName()`. Forge allows more than one `Fluid` instance per name - a mod keeps its
own object while the registry hands out a default - so two keys for the same fluid could hash the same and
still answer `false` to `equals`. A `HashMap` then holds both.

Second half of the same bug: `AEFluidKey.of(FluidStack)` kept an **empty** `NBTTagCompound` as-is, so a fluid
handed over with `tag = {}` made a different key than the same fluid with `tag = null`. Forge's own
`FluidStack` comparison treats those as distinct too, but a storage key must not: an empty compound carries
nothing. Both are normalised to null now, in `AEItemKey` as well.

Symptoms, all from one root and none of them looking like an equality problem:

- one fluid occupying two entries in a network, drawn as two identical terminal rows;
- the craft-plan screen listing the same fluid twice (`visual.contains` is an `equals` test);
- **a crafting job never completing** - the output arrived under a key that did not equal the one the job was
  waiting for, so the fluid reached the network and the job kept waiting.

**Rule:** every field `hashCode` reads, `equals` must read the same way. Where a platform type has no stable
identity - `Fluid` does not - compare by its registry name and hash the same string.

## 9.1d The placeholder must be unwrapped before it is read — use `GenericStack.resolveItemStack`

The most expensive defect of the fluids phase, and it wore three disguises.

`GenericStack.fromItemStack(ItemStack)` is the *raw* reading: it answers `AEItemKey.of(stack)`. Handed a
`WrappedGenericStack` placeholder it therefore answers **an item key for the display shim** - a key nothing in
the network will ever store. `PatternHelper` read both a pattern's inputs and its outputs that way, so a
processing pattern producing 40mB of a fluid declared its output as *one placeholder item*.

What that looked like from the game, none of it resembling the cause:

- **A duplicated terminal row.** The craftable key was `AEItemKey(placeholder)` and the stored key was
  `AEFluidKey(fish oil)`; they are not equal, so both got a row, and the placeholder renders with the fluid's
  own name and icon. The tell was in the tooltips: the real row said "Amount: 80mB" while the phantom said
  "Fish Oil: 40mB" - the latter is the *placeholder item's own* tooltip, printed from its NBT. Reading both
  tooltips is what finally identified this, after two wrong diagnoses.
- **A crafting job that never completed.** The job waited for `AEItemKey(placeholder)`; the machine delivered
  `AEFluidKey`. The fluid reached the network and the CPU kept waiting.
- **"Craft 3" producing three crafts of 40mB.** The pattern's output was one *item*, so three of them meant
  three runs rather than three millibuckets.

**Rule:** an `ItemStack` arriving from a slot, an inventory or a saved pattern goes through
`GenericStack.resolveItemStack`, never `fromItemStack`. Use the raw reading only where the stack cannot be a
placeholder by construction - a container item's remainder, a vanilla crafting result.

Fourth member of the §9.1 family, and the clearest statement of it: **any** helper that turns an `ItemStack`
into a key must be asked whether that stack could be a wrapper.

## 9.1e Dropping a parameter can turn an override into a different method

`ISaveProvider.saveChanges(ICellInventory<?>)` became `saveChanges()` in the port, matching upstream. That is
a fine API change. What it did to `TileChest` is the lesson:

```java
// before - the only implementor that used the argument
public void saveChanges(final ICellInventory<?> cellInventory) {
    if (cellInventory != null) {
        cellInventory.persist();       // <-- writes the cell's contents into its ItemStack
    }
    this.world.markChunkDirty(this.pos, this);
}

// after - still compiles, still carries @Override, and is now AEBaseTile.saveChanges()
```

The no-argument `saveChanges()` **already existed** on the supertype chain, so the migrated override simply
resolved to that one: mark the chunk dirty, and nothing else. `@Override` did not complain, because it was a
valid override - of a different method. Everything put into an ME Chest lived in the cell's in-memory
inventory and was never written down; taking the cell out lost the lot and its tooltip always read empty.
Drives were unaffected, because they hold cells in an `AppEngCellInventory` that persists them itself.

**Rule:** when a frozen-API method loses a parameter, check every implementor for a same-named method
inherited from elsewhere. `@Override` proves the method exists somewhere above; it does not prove it is the
one that used to be called. The other three pre-port implementors were no-ops, so the chest was the only
casualty - which is also why nothing else looked wrong.

## 9.2 Open note for wave 4 — `ICraftingJob.populatePlan`

The old crafting plan stored **two** numbers per key on one `IAEItemStack`: `stackSize` (used/missing) and `countRequestable` (to be produced by crafting). A `KeyCounter` holds one `long` per key, so the two cannot coexist in it.

Wave 2 solved this without touching the frozen API: `CraftingTreeNode`/`CraftingTreeProcess` thread two separate counters, the frozen `ICraftingJob.populatePlan(KeyCounter)` merges them, and an **additive** concrete overload `CraftingJob.populatePlan(KeyCounter used, KeyCounter requestable)` exposes the unmerged split.

The only caller is `ContainerCraftConfirm.java:201` (`this.result.populatePlan(plan)`), which is exactly the GUI that displays the used/craft split in separate columns. Wave 4 must therefore **hold the concrete `CraftingJob` type and call the two-argument overload**, otherwise the craft-confirm screen loses a distinction it used to show. If holding the concrete type turns out to be impractical, the alternative is amending `ICraftingJob` to take two counters — a frozen-API change requiring owner approval, so raise it rather than merging silently.

## 10. AE2UD-specific features that upstream does not have

Instructing agents to "mirror AE2-original" has a side effect: features the fork has and upstream lacks quietly disappear under literal mirroring. This has already happened twice. Rule 6 now forbids it outright, but the inventory below must be checked before writing code in the relevant area.

### Restored regressions

**Sticky Card.** The logic lived in `NetworkInventoryHandler`, which was replaced by `NetworkStorage`, in which upstream has no sticky concept (zero mentions in the entire AE2-original tree). The item stayed fully wired up:

| File | What |
| --- | --- |
| `core\Registration.java:367-401` | `Upgrades.STICKY` on all 8 cell types (item + fluid) |
| `core\api\definitions\ApiMaterials.java:212` | the `material.card.sticky` item |
| `container\ContainerStorageBus.java:117`, `ContainerOreDictStorageBus.java:51` | the `Settings.STICKY_MODE` setting |
| `core\api\ApiClientHelper.java:64` | `handler.isSticky()` in the tooltip |
| `lang\en_us.lang:259,556` | `Sticky Card`, `gui...Sticky` |

**Restored.** `MEInventoryHandler.isSticky()/setSticky()`, `BasicCellInventory.isSticky()` computed from the cell's upgrades at construction, propagated by `DriveWatcher`. In `NetworkStorage.insert()` a dedicated sticky pass runs first: if any sticky mount claims the key, the regular preferred/fallback passes are skipped entirely for that call, so there is no spillover into non-sticky storage even when the sticky mount cannot hold everything. `extract()` drains non-sticky mounts first. This is a deliberate divergence from upstream.

**Crafting CPU push notifications.** `addListener`/`removeListener`/`postChange` were removed from `CraftingCPUCluster` along with the `IMEMonitorHandlerReceiver` model, and polling was proposed instead. The owner rejected polling. **Restored** as `ICraftingCPUListener` (`onCraftingCPUChange(AEKey, IActionSource)`), with `postChange` calls back in every place the old code had them: `injectItems`, both extraction branches and both output loops in `executeCrafting`, `cancel()`, `submitJob()`, `storeItems()`.

### Third case: terminal live updates — resolved 2026-07-28

`ContainerMEMonitorable` is the base container of **every** ME terminal (regular, crafting, pattern, wireless, portable cell). Before the migration (`git show 1e855f729:src/main/java/appeng/container/implementations/ContainerMEMonitorable.java`) it did `this.monitor.addListener(this, null)` at line 116 and received live deltas in `postChange(IBaseMonitor<IAEItemStack>, Iterable<IAEItemStack>, IActionSource)` at line 369. That is how a terminal updates in real time.

Wave 2 removed `addListener`/`removeListener` from `WirelessTerminalGuiObject` on the grounds that watchers do not apply to a portable GUI object; wave 3's `PortableCellViewer` did the same. Two distinct cases hide behind that, and they get different answers:

1. **Network-backed terminals — real push.** Register an `IStorageWatcherNode` and call `IStackWatcher.setWatchAll(true)`, then handle `onStackChange(AEKey what, long amount)`. This is exactly what `setWatchAll` was added for, and upstream has no equivalent path.
2. **Portable cell / view-only cell terminals — server-side per-tick diff.** These view a `StorageCell` directly, with no grid node and therefore no watcher. Do what upstream's `MEStorageMenu.broadcastChanges()` does for *every* terminal: snapshot `getAvailableStacks()`, subtract the previous snapshot, send only the difference.

**Case 2 is not the polling rule 6 forbids.** What was rejected for crafting CPUs was making the *GUI* re-ask for state, which makes updates visibly lazy. Here the diff runs server-side once per tick and the client receives the same delta packets as before, so the player sees no difference. It is also cheap here specifically: the snapshot covers one cell's contents, not a whole network — which is why upstream can afford this approach for everything.

Neither case requires an addition to the frozen API.

### Inventory of at-risk features

**Upgrade cards.** AE2UD's `Upgrades` enum has 10 values; upstream moved to a registry entirely.

| Card | Where the logic lives | Status |
| --- | --- | --- |
| `STICKY` | `NetworkStorage` | restored |
| `PATTERN_EXPANSION` | `ContainerInterface:89,142`, `ContainerInterfaceTerminal:439`, `DualityInterface` | intact through wave 3 (`DualityInterface`, `PartInterface`, `TileInterface`); remaining risk is the two containers, **wave 4** |
| `MAGNET` | `UpgradeInventory:151`, `ItemMaterial:166` | intact, verified after wave 3 |
| `QUANTUM_LINK` | `UpgradeInventory:152`, `ItemMaterial:168`, `Registration:471` | intact, verified after wave 3 |

`CAPACITY`, `REDSTONE`, `CRAFTING`, `FUZZY`, `SPEED` and `INVERTER` exist upstream too — no mirroring risk.

**Settings with no upstream equivalent:** `STICKY_MODE`, `SEARCH_MODE`, `LEVEL_TYPE`, `UNLOCK`, `INTERFACE_TERMINAL`.

**Parts with no upstream equivalent:** `OREDICT_STORAGE_BUS`, `EXPANDED_PROCESSING_PATTERN_TERMINAL`, `INTERFACE_CONFIGURATION_TERMINAL`, `FLUID_INTERFACE_CONFIGURATION_TERMINAL`, `P2P_TUNNEL_GTEU`, `P2P_TUNNEL_IC2`, plus the whole `FLUID_*` set (inherited from the AE2FluidCraft line). Waves 3–5.

**Checked and NOT damaged:** `Settings.STORAGE_FILTER` — `MEMonitorIInventory:117` and `MEMonitorIFluidHandler:103` still honour `StorageFilter.EXTRACTABLE_ONLY` via `setMode(...)`. Replacing `setStorageFilter` with `setExtractFiltering` on `MEInventoryHandler` is a different axis and does not affect behaviour.

### The Fuzzy Card is decided per key type, not per part

Every bus and plane reads `Upgrades.FUZZY` the same way and hands the mode to `IPartitionList.Builder.fuzzyMode`; nothing downstream branches on the key type. The type-specific half lives entirely in the frozen API, in `AEKey.fuzzyEquals(AEKey, FuzzyMode)`, which dispatches through `AEKeyType.supportsFuzzyRangeSearch()`:

| Key type | `supportsFuzzyRangeSearch()` | `IGNORE_ALL` matches | Percentage modes (25/50/75/99%) |
|---|---|---|---|
| `AEItemKeyType` | `true` (overrides) | same item, damage ignored | compare remaining-durability against the mode's breakpoint |
| `AEFluidKeyType` | `false` (inherits the default) | same `Fluid`, NBT ignored (`getPrimaryKey()`) | fall back to exact `equals` — i.e. **precise matching** |

**A new key type gets fuzzy behaviour by overriding `supportsFuzzyRangeSearch()` plus `AEKey.getFuzzySearchValue()`/`getFuzzySearchMaxValue()`, and by nothing else.** No part, bus, plane or filter needs to learn about it. This is deliberate and is the reason `AEKeyType` is a Forge registry rather than a fixed pair.

Two consequences worth knowing before someone "fixes" them:

- For any type without a range concept, the percentage modes are *stricter* than `IGNORE_ALL`, not looser. The GUI still offers all four, because the setting is registered by the part and the part cannot know which types its filter will hold. That is not a bug in the part.
- Wave 5 made `PartFluidImportBus` and `PartFluidStorageBus` read the setting they had registered since before the port but never consulted, so the legacy fluid parts now behave like the generic `PartImportBus`/`PartStorageBus`. This removed an inconsistency rather than adding a fluid-only feature: a Fuzzy Card in a legacy fluid bus used to do nothing at all.
