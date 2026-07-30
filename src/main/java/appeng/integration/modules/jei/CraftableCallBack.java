package appeng.integration.modules.jei;

import appeng.api.config.FuzzyMode;
import appeng.api.stacks.AEItemKey;
import appeng.container.implementations.ContainerMEMonitorable;
import appeng.core.sync.network.NetworkHandler;
import appeng.core.sync.packets.PacketInventoryAction;
import appeng.helpers.InventoryAction;
import appeng.util.Platform;
import com.google.common.base.Stopwatch;
import mezz.jei.api.gui.ITooltipCallback;
import net.minecraft.item.ItemStack;
import net.minecraft.util.text.translation.I18n;
import org.lwjgl.input.Mouse;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class CraftableCallBack implements ITooltipCallback<ItemStack> {
    private final ContainerMEMonitorable container;
    private final Stopwatch lastClicked = Stopwatch.createStarted();


    public CraftableCallBack(ContainerMEMonitorable container) {
        this.container = container;
    }

    @Override
    public void onTooltip(int slotIndex, boolean input, ItemStack ingredient, List<String> tooltip) {
        if (!input) return;

        // Rebuilt per tooltip, as before. The old signature took a pre-merged list and then merged it a
        // second time on top of itself, double-counting the player inventory; the reason it merged at all
        // was to pick up inventory changes made while the recipe screen stayed open, and that is kept.
        final AvailableItems available = AvailableItems.merge(this.container);

        AEItemKey search = AEItemKey.of(ingredient);
        if (ingredient.getItem().isDamageable() || Platform.isGTDamageableItem(ingredient.getItem())) {
            Collection<AvailableItems.Entry> fuzzy = available.findFuzzy(search, FuzzyMode.IGNORE_ALL);
            if (fuzzy.size() > 0) {
                for (AvailableItems.Entry entry : fuzzy) {
                    if (entry.amount() > 0) {
                        if (Platform.isGTDamageableItem(ingredient.getItem())) {
                            if (!(ingredient.getMetadata() == ((AEItemKey) entry.what()).getDamage())) {
                                continue;
                            }
                        }

                        break;
                    } else {
                        String line = "§c[" + I18n.translateToLocalFormatted("gui.appliedenergistics2.Missing") + "]";
                        tooltip.add(line);
                        if (entry.craftable()) {
                            line = "§1[" + I18n.translateToLocalFormatted("gui.tooltips.appliedenergistics2.Craftable") + "]";
                            tooltip.add(line);
                            if (Mouse.isButtonDown(2) && this.lastClicked.elapsed(TimeUnit.MILLISECONDS) > 200) {
                                this.lastClicked.reset().start();
                                this.container.setTargetStack(entry.what());
                                final PacketInventoryAction p = new PacketInventoryAction(InventoryAction.AUTO_CRAFT, this.container.getInventory().size(), 0);
                                NetworkHandler.instance().sendToServer(p);
                            }
                        }
                    }
                }
            } else {
                String line = "§c[" + I18n.translateToLocalFormatted("gui.appliedenergistics2.Missing") + "]";
                tooltip.add(line);
            }
        } else {
            AvailableItems.Entry found = available.findPrecise(search);
            if (found != null) {
                if (found.amount() == 0) {
                    String line = "§c[" + I18n.translateToLocalFormatted("gui.appliedenergistics2.Missing") + "]";
                    tooltip.add(line);
                }
                if (found.craftable()) {
                    String line = "§1[" + I18n.translateToLocalFormatted("gui.tooltips.appliedenergistics2.Craftable") + "]";
                    tooltip.add(line);
                    if (Mouse.isButtonDown(2) && this.lastClicked.elapsed(TimeUnit.MILLISECONDS) > 200) {
                        this.lastClicked.reset().start();
                        this.container.setTargetStack(found.what());
                        final PacketInventoryAction p = new PacketInventoryAction(InventoryAction.AUTO_CRAFT, this.container.getInventory().size(), 0);
                        NetworkHandler.instance().sendToServer(p);
                    }
                }
            } else {
                String line = "§c[" + I18n.translateToLocalFormatted("gui.appliedenergistics2.Missing") + "]";
                tooltip.add(line);
            }
        }
    }
}
