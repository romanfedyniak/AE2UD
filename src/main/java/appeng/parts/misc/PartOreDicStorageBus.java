package appeng.parts.misc;

import appeng.api.upgrades.UpgradeCards;

import appeng.api.parts.IPartModel;
import appeng.core.AppEng;
import appeng.core.sync.GuiBridge;
import appeng.items.parts.PartModels;
import appeng.parts.PartModel;
import appeng.util.Platform;
import appeng.util.item.OreDictFilterMatcher;
import appeng.util.prioritylist.IPartitionList;
import appeng.util.prioritylist.OreDictPriorityList;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumHand;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.Vec3d;


/**
 * Ore-dictionary filtering storage bus. AE2UD-specific: upstream AE2 has no ore-dictionary concept at all (see
 * CONTRACT.md §10). The only thing this subclass changes relative to {@link PartStorageBus} is the filter list
 * itself ({@link #createFilter()}) - ACCESS, STORAGE_FILTER, UpgradeCards.inverter() (whitelist/blacklist),
 * UpgradeCards.sticky() and priority are all inherited, unchanged, from {@link PartStorageBus#getInternalHandler()}.
 */
public class PartOreDicStorageBus extends PartStorageBus {
    public static final ResourceLocation MODEL_BASE = new ResourceLocation(AppEng.MOD_ID, "part/oredict_storage_bus_base");
    @PartModels
    public static final IPartModel MODELS_OFF = new PartModel(MODEL_BASE, new ResourceLocation(AppEng.MOD_ID, "part/storage_bus_off"));
    @PartModels
    public static final IPartModel MODELS_ON = new PartModel(MODEL_BASE, new ResourceLocation(AppEng.MOD_ID, "part/storage_bus_on"));
    @PartModels
    public static final IPartModel MODELS_HAS_CHANNEL = new PartModel(MODEL_BASE, new ResourceLocation(AppEng.MOD_ID, "part/storage_bus_has_channel"));

    public String oreExp = "";
    private OreDictPriorityList priorityList;

    public PartOreDicStorageBus(ItemStack is) {
        super(is);
    }

    @Override
    public void readFromNBT(NBTTagCompound data) {
        super.readFromNBT(data);
        this.oreExp = data.getString("oreMatch");

        var rulesList = OreDictFilterMatcher.parseExpression(oreExp);
        this.priorityList = new OreDictPriorityList(rulesList);
    }

    @Override
    public void writeToNBT(NBTTagCompound data) {
        super.writeToNBT(data);
        data.setString("oreMatch", getOreExp());
    }

    @Override
    public boolean onPartActivate(final EntityPlayer player, final EnumHand hand, final Vec3d pos) {
        if (Platform.isServer()) {
            Platform.openGUI(player, this.getHost().getTile(), this.getSide(), GuiBridge.GUI_OREDICTSTORAGEBUS);
        }
        return true;
    }

    @Override
    public GuiBridge getGuiBridge() {
        return GuiBridge.GUI_OREDICTSTORAGEBUS;
    }

    @Override
    protected IPartitionList createFilter() {
        return this.getPriorityList();
    }

    private IPartitionList getPriorityList() {
        if (priorityList == null) {
            var ruleList = OreDictFilterMatcher.parseExpression(oreExp);
            priorityList = new OreDictPriorityList(ruleList);
        }
        return priorityList;
    }

    public String getOreExp() {
        if (this.oreExp == null) {
            return "";
        }
        return oreExp;
    }

    public void saveOreMatch(String oreMatch) {
        if (!this.oreExp.equals(oreMatch)) {
            this.oreExp = oreMatch;

            var ruleList = OreDictFilterMatcher.parseExpression(oreMatch);
            this.priorityList = new OreDictPriorityList(ruleList);
            this.resetCache(true);
            this.getHost().markForSave();
        }
    }

    @Override
    public IPartModel getStaticModels() {
        if (this.isActive() && this.isPowered()) {
            return MODELS_HAS_CHANNEL;
        } else if (this.isPowered()) {
            return MODELS_ON;
        } else {
            return MODELS_OFF;
        }
    }

}
