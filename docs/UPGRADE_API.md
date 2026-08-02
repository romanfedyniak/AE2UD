# Upgrade card API

AE2UD uses item-based upgrade cards. Cards and upgradable hosts are matched by `Item` and metadata;
NBT is intentionally ignored. Addons do not implement an interface or extend an AE2UD item class.

Get the registry during normal mod loading:

```java
IUpgradeRegistry upgrades = AEApi.instance().registries().upgrades();
```

## Exact card support

Register an arbitrary card for a specific machine, part, or item:

```java
upgrades.add(new ItemStack(myCard, 1, metadata), myMachineStack, 2);
```

The machine can then query its `IUpgradeInventory`:

```java
int installed = upgradeInventory.getInstalledUpgrades(myCardStack);
```

Repeated identical registrations are harmless. Conflicting registrations and non-positive limits fail
immediately with `IllegalArgumentException`.

## Speed cards

One speed point is equivalent to one standard Acceleration Card. The default registration inherits every
machine and part registered for standard speed-card support, except the Matter Cannon:

```java
upgrades.registerSpeedCard(myFastCardStack, 100);
```

Use the overload with `false` to require exact associations instead:

```java
upgrades.registerSpeedCard(myFastCardStack, 100, false);
upgrades.add(myFastCardStack, myMachineStack, 4);
```

Speed points are not capped by the host. The physical card count and available upgrade slots are the only
limits. AE2UD sums points with saturating arithmetic, preserves the standard speed curves, and safely
extends them past their former maximums. Power consumption continues to scale with work performed.

## Capacity cards

Capacity points replace standard Capacity Cards and inherit standard capacity-compatible hosts by default:

```java
upgrades.registerCapacityCard(myCapacityCardStack, 5);
```

Unlike speed, effective capacity is capped by the host. For an addon machine that accepts only an exact
custom capacity card, register its limit separately:

```java
upgrades.registerCapacityCard(myCapacityCardStack, 20, false);
upgrades.add(myCapacityCardStack, myMachineStack, 1);
upgrades.setCapacityLimit(myMachineStack, 20);
```

The same card may carry both speed and capacity traits. It occupies one physical slot and contributes both
effects wherever the host supports them.

## Upgrade inventories

Addon machines and items can use the same filtered inventory implementation as AE2UD:

```java
IUpgradeInventory machineUpgrades = UpgradeInventories.forMachine(
        myMachineStack,
        4,
        inventory -> markDirty());
```

Machine owners serialize the inventory through `readFromNBT` and `writeToNBT`. Item inventories created
with `UpgradeInventories.forItem` store themselves in the host stack's `upgrades` tag automatically.

Registered cards receive automatic tooltips showing speed/capacity points and compatible hosts. The
original item tooltip remains intact.
