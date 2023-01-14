package com.rusefi;

import com.rusefi.xml.*;

import java.io.FileWriter;
import java.io.IOException;
import java.util.Date;

/**
 * Unused and replaced by http://rusefi.online?
 * REMOVE ONE DAY PRETTY SOON
 */
public class MdGenerator {
    public static final String PREFIX = "rusEFI-project";
    static String FOLDER;

    private static final String EOL = "\r\n";

    public static void main(String[] args) throws Exception {
        //FOLDER = "images/";
        FOLDER = "overview/TS_generated/";

        ContentModel contentModel = XmlUtil.readModel(ContentModel.class, ScreenGenerator.FILE_NAME);

        generateTopLevel(contentModel);

        for (TopLevelMenuModel topLevelMenuModel : contentModel.getTopLevelMenus()) {
            String pageName = getPageName(topLevelMenuModel);
            FileWriter md = new FileWriter(pageName + ".md");

            md.append("# [rusEFI project](rusEFI-project)"+ EOL);

            md.append("## " + topLevelMenuModel.getTitle() + EOL);

            for (DialogModel dialogModel : topLevelMenuModel.getDialogs()) {
                md.append("[" + dialogModel.getDialogTitle() + "](" + "#" + safeUrl(dialogModel.getDialogTitle()).toLowerCase() + ")" + EOL + EOL);
            }


            for (DialogModel dialogModel : topLevelMenuModel.getDialogs()) {
                appendDialog(md, dialogModel);
            }

            md.write(EOL);
            md.write("generated by " + MdGenerator.class + " on " + new Date());
            md.write(EOL);
            md.close();
        }
    }

    private static String getPageName(TopLevelMenuModel topLevelMenuModel) {
        return PREFIX + "-" + safeUrl(topLevelMenuModel.getTitle());
    }

    private static String safeUrl(String title) {
        return title.replace(" ", "-");
    }

    private static void generateTopLevel(ContentModel contentModel) throws IOException {
        FileWriter md = new FileWriter(PREFIX + ".md");

        for (TopLevelMenuModel topLevelMenuModel : contentModel.getTopLevelMenus()) {
            String url = getPageName(topLevelMenuModel);
            md.append("# [" + topLevelMenuModel.getTitle() + "](" + url + ")" + EOL + EOL);

            md.append("<a href='" + url + "'>" + getImageTag(topLevelMenuModel.getImageName()) + "</a>");

            for (DialogModel dialogModel : topLevelMenuModel.getDialogs()) {
                md.append("[" + dialogModel.getDialogTitle() + "](" + url + "#" + safeUrl(dialogModel.getDialogTitle()).toLowerCase() + ")" + EOL + EOL);
            }

            md.append(EOL);
        }
        md.write(EOL);
        md.write("generated by " + MdGenerator.class + " on " + new Date());
        md.write(EOL);
        md.close();
    }

    private static void appendDialog(FileWriter md, DialogModel dialogModel) throws IOException {
        md.append("### " + dialogModel.getDialogTitle() + EOL);

        md.append(getImageTag(dialogModel.getImageName()) + EOL);

        for (FieldModel fieldModel : dialogModel.fields) {
            String tooltip = fieldModel.getTooltip();

            if (tooltip.length() > 0) {
                tooltip = tooltip.replace("\\n", EOL);

                md.append(fieldModel.getUiName() + ": " + tooltip + EOL + EOL);
            }
        }
    }

    private static String getImageTag(String imageName) {
        String IMG_SUFFIX = ")" + EOL;
        String IMG_PREFIX = "![x](";

        return IMG_PREFIX + FOLDER + imageName + IMG_SUFFIX;
    }
}
