package appeng.core.localization;

import appeng.api.config.PowerUnits;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import net.minecraft.item.ItemStack;
import com.github.bsideup.jabel.Desugar;
import net.minecraft.util.text.*;

import java.text.DecimalFormat;
import java.text.MessageFormat;
import java.util.Arrays;

/**
 * Static utilities for constructing tooltips in various places.
 */
public final class Tooltips {

    private static final char SEP;
    static {
        var format = (DecimalFormat) DecimalFormat.getInstance();
        var symbols = format.getDecimalFormatSymbols();
        SEP = symbols.getDecimalSeparator();
    }

    public static final Style UNIT_TEXT = new Style().setColor(TextFormatting.YELLOW).setItalic(false);
    public static final Style NORMAL_TOOLTIP_TEXT = new Style().setColor(TextFormatting.GRAY).setItalic(false);
    public static final Style NUMBER_TEXT = new Style().setColor(TextFormatting.LIGHT_PURPLE).setItalic(false);

    /** What a click does, and the button that does it: the button steps back so the action reads first. */
    public static final Style ACTION_TEXT = new Style().setColor(TextFormatting.WHITE).setItalic(false);
    public static final Style MUTED_TEXT = new Style().setColor(TextFormatting.DARK_GRAY).setItalic(false);


    public static final String[] units = new String[] { "k", "M", "G", "T", "P", "E" };
    public static final long[] DECIMAL_NUMS = new long[] { 1000L, 1000_000L, 1000_000_000L, 1000_000_000_000L,
            1000_000_000_000_000L,
            1000_000_000_000_000_000L };

    public static ITextComponent of(ITextComponent... components) {
        ITextComponent s = new TextComponentString("");
        for (var c : components) {
            s = s.appendSibling(c);
        }
        return s;
    }

    public static ITextComponent of(String s) {
        return new TextComponentString(s);
    }

    public static ITextComponent ofPercent(double percent) {
        return ofPercent(percent,true);
    }

    public static ITextComponent ofPercent(double percent, boolean oneIsGreen) {
        return new TextComponentString(MessageFormat.format("{0,number,#.##%}", percent))
                .setStyle(colorFromRatio(percent, oneIsGreen));
    }

    public static ITextComponent of(PowerUnits pU) {
        return new TextComponentTranslation(pU.unlocalizedName).setStyle(UNIT_TEXT);
    }

    public static ITextComponent ofNumber(double number, double max) {
        MaxedAmount amount = getMaxedAmount(number, max);
        return ofNumber(amount);
    }

    public static ITextComponent of(GuiText guiText, Object... args) {
        return of(guiText, NORMAL_TOOLTIP_TEXT, args);
    }

    public static ITextComponent of(GuiText guiText, Style style, Object... args) {

        if (args.length > 0 && args[0] instanceof Integer) {
            return guiText.getLocalizedWithArgs(Arrays.stream(args).map((o) -> ofUnformattedNumber((Integer) o)).toArray()).createCopy()
                    .setStyle(style);
        } else if (args.length > 0 && args[0] instanceof Long) {
            return guiText.getLocalizedWithArgs(Arrays.stream(args).map((o) -> ofUnformattedNumber((Long) o)).toArray()).createCopy()
                    .setStyle(style);
        }
        return guiText.getLocalizedWithArgs(args).createCopy().setStyle(style);

    }

    /**
     * A line saying what one click on a slot would do - the whole thing one translated string, so that a
     * language wanting the button after the verb can have it that way.
     */
    public static ITextComponent action(final ButtonToolTips line, final Object... args) {
        return line.getLocalizedWithArgs(args).createCopy().setStyle(ACTION_TEXT);
    }

    /** The name of a mouse button, for the first argument of every action line. */
    public static ITextComponent click(final int mouseButton) {
        final ButtonToolTips name;
        switch (mouseButton) {
            case 0:
                name = ButtonToolTips.LeftClick;
                break;
            case 1:
                name = ButtonToolTips.RightClick;
                break;
            case 2:
                name = ButtonToolTips.MiddleClick;
                break;
            default:
                return muted(ButtonToolTips.MouseButton.getLocalizedWithArgs(mouseButton));
        }

        return muted(new TextComponentString(name.getLocal()));
    }

    /**
     * What a terminal-style search field understands. Any field running the same search says the same
     * thing, so it is written here rather than on the screen that happened to need it first.
     */
    public static String searchSyntax() {
        return String.join("\n", GuiText.SearchHintTitle.getLocal(), GuiText.SearchHintName.getLocal(),
                GuiText.SearchHintTerms.getLocal(), GuiText.SearchHintOr.getLocal(),
                GuiText.SearchHintPhrase.getLocal(), GuiText.SearchHintExclude.getLocal(),
                GuiText.SearchHintMod.getLocal(), GuiText.SearchHintTooltip.getLocal(),
                GuiText.SearchHintOreDict.getLocal(), GuiText.SearchHintId.getLocal(),
                GuiText.SearchHintRegex.getLocal());
    }

    public static ITextComponent muted(final ITextComponent text) {
        return text.createCopy().setStyle(MUTED_TEXT);
    }

    /**
     * A thing named in its own colour, the way an item is named in the colour of its rarity. Copied before
     * it is styled: a key's display name is cached and shared with the terminal's search field, with Waila
     * and with The One Probe, none of which want a colour in it.
     */
    public static ITextComponent nameOf(final ItemStack stack) {
        return new TextComponentString(stack.getDisplayName())
                .setStyle(new Style().setColor(stack.getRarity().color).setItalic(false));
    }

    public static ITextComponent nameOf(final AEKey what) {
        if (what instanceof AEItemKey) {
            return nameOf(((AEItemKey) what).getReadOnlyStack());
        }

        return what.getDisplayName().createCopy()
                .setStyle(new Style().setColor(what.getType().getDisplayColour()).setItalic(false));
    }

    public static ITextComponent ofUnformattedNumber(long number) {
        return new TextComponentString(String.valueOf(number)).setStyle(NUMBER_TEXT);
    }

    public static ITextComponent ofUnformattedNumberWithRatioColor(long number, double ratio, boolean oneIsGreen) {
        return new TextComponentString(String.valueOf(number)).setStyle(colorFromRatio(ratio, oneIsGreen));
    }

    private static ITextComponent ofNumber(MaxedAmount number) {
        boolean numberUnit = !number.digit().equals("0");
        return new TextComponentString(number.digit() + (numberUnit ? number.unit() : "")).setStyle(NUMBER_TEXT)
                .appendSibling(new TextComponentString("/")
                        .setStyle(NORMAL_TOOLTIP_TEXT))
                .appendText(number.maxDigit() + number.unit()).setStyle(NUMBER_TEXT);
    }
    @Desugar
    public record MaxedAmount(String digit, String maxDigit, String unit) {
    }

    @Desugar
    public record Amount(String digit, String unit) {
    }

    public static Style colorFromRatio(double ratio, boolean oneIsGreen) {
        double p = ratio;

        if (!oneIsGreen) {
            p = 1 - p;
        }

        TextFormatting colorCode = getColorCode(p);

        return new Style().setItalic(false).setColor(colorCode);
    }

    public static String getAmount(double amount, long num) {
        double fract = amount / num;
        String returned;
        if (fract < 10) {
            returned = String.format("%.3f", fract);
        } else if (fract < 100) {
            returned = String.format("%.2f", fract);
        } else {
            returned = String.format("%.1f", fract);
        }
        while (returned.endsWith("0")) {
            returned = returned.substring(0, returned.length() - 1);
        }
        if (returned.endsWith(String.valueOf(SEP))) {
            returned = returned.substring(0, returned.length() - 1);
        }
        return returned;

    }

    public static Amount getAmount(double amount) {
        if (amount < 10000) {
            return new Amount(getAmount(amount, 1), "");
        } else {
            int i = 0;
            while (amount / DECIMAL_NUMS[i] >= 1000) {
                i++;
            }
            return new Amount(getAmount(amount, DECIMAL_NUMS[i]), units[i]);
        }
    }

    public static MaxedAmount getMaxedAmount(double amount, double max) {
        if (max < 10000) {
            return new MaxedAmount(getAmount(amount, 1), getAmount(max, 1), "");
        } else {
            int i = 0;
            while (max / DECIMAL_NUMS[i] >= 1000) {
                i++;
            }
            return new MaxedAmount(getAmount(amount, DECIMAL_NUMS[i]), getAmount(max, DECIMAL_NUMS[i]), units[i]);
        }
    }

    private static TextFormatting getColorCode(double p) {
        if (p < 0.33) {
            return TextFormatting.RED;
        } else if (p < 0.66) {
            return TextFormatting.YELLOW;
        } else {
            return TextFormatting.GREEN;
        }
    }

    public static ITextComponent energyStorageComponent(double energy, double max) {
        return Tooltips.of(
                Tooltips.of(GuiText.StoredEnergy.getLocal()),
                Tooltips.of(": "),
                Tooltips.ofNumber(energy, max),
                Tooltips.of(" "),
                Tooltips.of(PowerUnits.AE),
                Tooltips.of(" ("),
                Tooltips.ofPercent(energy / max),
                Tooltips.of(")"));
    }

    public static ITextComponent bytesUsed(long bytes, long max) {
        return of(
                Tooltips.of(
                        ofUnformattedNumberWithRatioColor(bytes, (double) bytes / max, false),
                        of(" "),
                        of(GuiText.Of),
                        of(" "),
                        ofUnformattedNumber(max),
                        of(" "),
                        of(GuiText.BytesUsed)));
    }

    public static ITextComponent typesUsed(long types, long max) {
        return Tooltips.of(
                ofUnformattedNumberWithRatioColor(types, (double) types / max, false),
                of(" "),
                of(GuiText.Of),
                of(" "),
                ofUnformattedNumber(max),
                of(" "),
                of(GuiText.Types));
    }
}
