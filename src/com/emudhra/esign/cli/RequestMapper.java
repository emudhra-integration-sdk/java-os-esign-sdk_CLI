package com.emudhra.esign.cli;

import com.emudhra.esign.AdvanceSignature;
import com.emudhra.esign.ColoredGraphicInputs;
import com.emudhra.esign.ContentSearch;
import com.emudhra.esign.CustomStyle;
import com.emudhra.esign.Enums;
import com.emudhra.esign.eSign;
import com.emudhra.esign.eSignInput;
import com.emudhra.esign.eSignInputBuilder;
import com.emudhra.esign.pdf.model.PdfColor;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

public class RequestMapper {

    public static ArrayList<eSignInput> toInputList(List<CliDocument> docs) throws IOException {
        ArrayList<eSignInput> inputs = new ArrayList<>();
        for (CliDocument doc : docs) {
            inputs.add(toInput(doc));
        }
        return inputs;
    }

    private static eSignInput toInput(CliDocument doc) throws IOException {
        eSignInputBuilder builder = eSignInputBuilder.init();

        // PDF source — file path takes priority over inline base64
        if (doc.docFilePath != null && !doc.docFilePath.isEmpty()) {
            byte[] pdfBytes = Files.readAllBytes(Paths.get(doc.docFilePath));
            builder.setDocBase64(Base64.getEncoder().encodeToString(pdfBytes));
        } else if (doc.docBase64 != null && !doc.docBase64.isEmpty()) {
            builder.setDocBase64(doc.docBase64);
        }

        if (notEmpty(doc.docHash))        builder.setDocHash(doc.docHash);
        if (notEmpty(doc.docInfo))        builder.setDocInfo(doc.docInfo);
        if (notEmpty(doc.docURL))         builder.setDocURL(doc.docURL);
        if (notEmpty(doc.signedBy))       builder.setSignedBy(doc.signedBy);
        if (notEmpty(doc.location))       builder.setLocation(doc.location);
        if (notEmpty(doc.reason))         builder.setReason(doc.reason);
        if (notEmpty(doc.appearanceText)) builder.setAppearanceText(doc.appearanceText);
        if (notEmpty(doc.oneLiner))       builder.setOneLiner(doc.oneLiner);
        if (notEmpty(doc.pdfPassword))    builder.setPdfPassword(doc.pdfPassword);

        builder.setCoSign(doc.coSign);
        builder.isRightOrigin(doc.rightOrigin);
        builder.setBorderRequired(doc.borderRequired);
        builder.setTickRequired(doc.tickRequired);
        builder.setPatchSignatureAppearance(doc.patchSignatureAppearance);

        if (doc.signatureFontSize > 0) builder.setSignatureFontSize(doc.signatureFontSize);

        // Signature image — file path takes priority
        if (notEmpty(doc.signatureImagePath)) {
            byte[] img = Files.readAllBytes(Paths.get(doc.signatureImagePath));
            builder.setSignatureImage(Base64.getEncoder().encodeToString(img));
        } else if (notEmpty(doc.signatureImage)) {
            builder.setSignatureImage(doc.signatureImage);
        }

        // Enums with sensible defaults
        builder.setInputType(notEmpty(doc.inputType)
            ? eSign.InputType.valueOf(doc.inputType)
            : eSign.InputType.PDF);
        builder.setAppearanceType(notEmpty(doc.appearanceType)
            ? eSign.AppearanceType.valueOf(doc.appearanceType)
            : eSign.AppearanceType.StandardSignature);
        builder.setPageTobeSigned(notEmpty(doc.pageTobeSigned)
            ? eSign.PageTobeSigned.valueOf(doc.pageTobeSigned)
            : eSign.PageTobeSigned.Last);

        if (notEmpty(doc.coordinates))           builder.setCoordinates(eSign.Coordinates.valueOf(doc.coordinates));
        if (notEmpty(doc.pageNumbers))           builder.setPageNumbers(doc.pageNumbers);

        // SDK requires pageLevelCoordinates to be non-empty even when using named coordinates
        // (the actual placement still comes from the Coordinates enum, not this value).
        // Auto-set a placeholder so callers don't need to supply it for standard modes.
        String plc = doc.pageLevelCoordinates;
        if (!notEmpty(plc)) {
            String pt = doc.pageTobeSigned != null ? doc.pageTobeSigned : "Last";
            if (!pt.equalsIgnoreCase("PageLevel")) {
                plc = coordinatesToPageLevel(doc.coordinates != null ? doc.coordinates : "BottomRight");
            }
        }
        if (notEmpty(plc)) builder.setPageLevelCoordinates(plc);

        // ContentSearch
        if (doc.contentSearch != null) {
            ContentSearch cs = new ContentSearch();
            cs.setSearchText(doc.contentSearch.searchText);
            if (doc.contentSearch.height > 0) cs.setHeight(doc.contentSearch.height);
            if (doc.contentSearch.width  > 0) cs.setWidth(doc.contentSearch.width);
            if (notEmpty(doc.contentSearch.offset))   cs.setOffset(doc.contentSearch.offset);
            if (notEmpty(doc.contentSearch.position)) cs.setPosition(ContentSearch.Position.valueOf(doc.contentSearch.position));
            builder.setContentSearch(cs);
        }

        // AdvanceSignature
        if (doc.advanceSignature != null) {
            AdvanceSignature as = new AdvanceSignature();
            if (notEmpty(doc.advanceSignature.imagePath)) {
                byte[] img = Files.readAllBytes(Paths.get(doc.advanceSignature.imagePath));
                as.setImagebase64(Base64.getEncoder().encodeToString(img));
            } else if (notEmpty(doc.advanceSignature.imageBase64)) {
                as.setImagebase64(doc.advanceSignature.imageBase64);
            }
            if (notEmpty(doc.advanceSignature.leftSideText))  as.setLeftSideText(doc.advanceSignature.leftSideText);
            if (notEmpty(doc.advanceSignature.rightSideText)) as.setRightSideText(doc.advanceSignature.rightSideText);
            if (notEmpty(doc.advanceSignature.imageType))     as.setImageType(Enums.ImageType.valueOf(doc.advanceSignature.imageType));
            builder.setAdvanceSignature(as);
        }

        // ColoredGraphicInputs
        if (doc.coloredGraphicInputs != null) {
            ColoredGraphicInputs cgi = new ColoredGraphicInputs();
            if (doc.coloredGraphicInputs.leftBorder  != null) cgi.setLeftBorder(doc.coloredGraphicInputs.leftBorder);
            if (doc.coloredGraphicInputs.rightBorder != null) cgi.setRightBorder(doc.coloredGraphicInputs.rightBorder);
            builder.setColoredGraphicInputs(cgi);
        }

        // CustomStyle
        if (doc.customStyle != null) {
            CustomStyle style = new CustomStyle();
            if (notEmpty(doc.customStyle.border))   style.setBorder(doc.customStyle.border);
            if (notEmpty(doc.customStyle.fontSize)) style.setFontSize(doc.customStyle.fontSize);
            if (doc.customStyle.fontColor != null)
                style.setFontColor(new PdfColor(
                    doc.customStyle.fontColor.r,
                    doc.customStyle.fontColor.g,
                    doc.customStyle.fontColor.b));
            builder.setCustomStyle(style);
        }

        return builder.build();
    }

    private static boolean notEmpty(String s) {
        return s != null && !s.isEmpty();
    }

    /** Converts a named Coordinates enum value to a page-level coordinate placeholder (page 1). */
    private static String coordinatesToPageLevel(String coordName) {
        String xy;
        switch (coordName) {
            case "TopLeft":      xy = "25,725,145,785";   break;
            case "TopMiddle":    xy = "225,725,345,785";  break;
            case "TopRight":     xy = "425,725,545,785";  break;
            case "CenterLeft":   xy = "25,425,145,485";   break;
            case "CenterMiddle": xy = "225,425,345,485";  break;
            case "CenterRight":  xy = "425,425,545,485";  break;
            case "BottomLeft":   xy = "25,100,145,160";   break;
            case "BottomMiddle": xy = "225,100,345,160";  break;
            case "BottomRight":
            default:             xy = "425,100,545,160";  break;
        }
        return "1-" + xy;
    }
}
