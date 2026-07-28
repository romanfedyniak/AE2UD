# Storage API contract (generic storage port)

This is the **frozen specification** of the `src/api` surface for the generic storage port. The research that led to these decisions lives in the wiki (`Z:\harmony\wiki\AE2UD`); this file carries only the *what*, not the *why*.

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
                              TileEntity host, int fortuneLevel, boolean silkTouch,
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

> **Deviation from upstream in `PickupStrategy.Factory`:** AE2-original passes `ItemEnchantments`, a modern data type with no 1.12.2 equivalent, so we pass the decomposed values — `int fortuneLevel, boolean silkTouch`. Those are the only two enchantments the annihilation plane actually reads.

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
            TileEntity host, int fortuneLevel, boolean silkTouch, @Nullable UUID owningPlayerId);
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

The ones marked "name unchanged" still move into the new package along with the rest, so the package layout matches AE2-original and backports do not have to fix imports by hand.

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
| 6    | `appeng.integration` (plus the switch to HEI, §8.2), NAE2 migration                                             |   ~34 | **green build** + manual in-game test |

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

The modpack uses **HadEnoughItems** (`Z:\harmony\sources\HadEnoughItems`, CleanroomMC, `51d34dba` @ 2026-07-10, version 4.32.0) — a 1.12.2 fork of JEI, not upstream JEI.

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
public IMEAdaptor(MEStorage input, IActionSource src);              // extends InventoryAdaptor
public IMEAdaptorIterator(IMEAdaptor parent, KeyCounter availableItems);
public IMEInventoryDestination(MEStorage o);
public boolean IMEInventoryDestination.canInsert(ItemStack stack);
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

## 9.1 Standing hazard: `GenericStack.equals()` is not `IAEItemStack.equals()`

**Every remaining wave must check this.** The old `IAEItemStack.equals()` **ignored the stack size** — it meant "the same item". `GenericStack` is a record, so its `equals()` compares **the amount as well**.

A literal translation therefore compiles cleanly and silently changes behaviour. Wave 2 found a real instance: `CraftingTreeProcess.addProcess()` and `getTimes()` compared a *condensed* (summed) amount against a *per-slot* amount and relied on size-insensitive equality. Translated literally, any recipe using the same item in more than one slot would have quietly failed to match.

**Rule:** when the old code compared stacks for identity, compare `a.what().equals(b.what())`, not `a.equals(b)`. Only compare whole `GenericStack`s when the amount genuinely is part of the comparison. Audit every `.equals(` you carry over from a stack comparison, and say in your report which ones you checked.

Audited clean as of wave 2: `appeng.me`, `appeng.crafting`, `appeng.helpers`, `appeng.util`, `appeng.tile`, `appeng.core`.

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

### Third case: terminal live updates — decide before wave 4

`ContainerMEMonitorable` is the base container of **every** ME terminal (regular, crafting, pattern, wireless, portable cell). Before the migration (`git show 1e855f729:src/main/java/appeng/container/implementations/ContainerMEMonitorable.java`) it did `this.monitor.addListener(this, null)` at line 116 and received live deltas in `postChange(IBaseMonitor<IAEItemStack>, Iterable<IAEItemStack>, IActionSource)` at line 369. That is how a terminal updates in real time.

Wave 2 removed `addListener`/`removeListener` from `WirelessTerminalGuiObject` on the grounds that watchers do not apply to a portable GUI object. Two distinct cases hide behind that, and only one of them has a replacement:

1. **Network-backed terminals** — the replacement exists and is correct: register an `IStorageWatcherNode` and call `IStackWatcher.setWatchAll(true)`, then handle `onStackChange(AEKey what, long amount)`. This is exactly what `setWatchAll` was added for.
2. **Portable cell / view-only cell terminals** — these view a `StorageCell` directly, with no grid node and therefore no watcher. **There is no replacement yet.** Per rule 6, polling is not an acceptable answer. Wave 4 needs a small push interface on this path, in the same spirit as `ICraftingCPUListener` (§9).

Wave 4 must not begin `ContainerMEMonitorable` until case 2 has an agreed design.

### Inventory of at-risk features

**Upgrade cards.** AE2UD's `Upgrades` enum has 10 values; upstream moved to a registry entirely.

| Card | Where the logic lives | Status |
| --- | --- | --- |
| `STICKY` | `NetworkStorage` | restored |
| `PATTERN_EXPANSION` | `ContainerInterface:89,142`, `ContainerInterfaceTerminal:439`, `DualityInterface` | at risk, waves 2–4 |
| `MAGNET` | `UpgradeInventory:151`, `ItemMaterial:166` | at risk, wave 3 |
| `QUANTUM_LINK` | `UpgradeInventory:152`, `ItemMaterial:168` | at risk, wave 3 |

`CAPACITY`, `REDSTONE`, `CRAFTING`, `FUZZY`, `SPEED` and `INVERTER` exist upstream too — no mirroring risk.

**Settings with no upstream equivalent:** `STICKY_MODE`, `SEARCH_MODE`, `LEVEL_TYPE`, `UNLOCK`, `INTERFACE_TERMINAL`.

**Parts with no upstream equivalent:** `OREDICT_STORAGE_BUS`, `EXPANDED_PROCESSING_PATTERN_TERMINAL`, `INTERFACE_CONFIGURATION_TERMINAL`, `FLUID_INTERFACE_CONFIGURATION_TERMINAL`, `P2P_TUNNEL_GTEU`, `P2P_TUNNEL_IC2`, plus the whole `FLUID_*` set (inherited from the AE2FluidCraft line). Waves 3–5.

**Checked and NOT damaged:** `Settings.STORAGE_FILTER` — `MEMonitorIInventory:117` and `MEMonitorIFluidHandler:103` still honour `StorageFilter.EXTRACTABLE_ONLY` via `setMode(...)`. Replacing `setStorageFilter` with `setExtractFiltering` on `MEInventoryHandler` is a different axis and does not affect behaviour.
