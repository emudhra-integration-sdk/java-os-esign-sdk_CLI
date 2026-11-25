package com.emudhra.esign.cli;

public class CliDocument {

    // Input — file path is preferred over inline base64
    public String docFilePath;
    public String docBase64;
    public String docHash;

    // Output
    public String outputPath;

    // Metadata
    public String docInfo;
    public String docURL;
    public String signedBy;
    public String location;
    public String reason;
    public String appearanceText;
    public String oneLiner;

    // Appearance
    public String appearanceType;
    public String pageTobeSigned;
    public String coordinates;
    public String pageNumbers;
    public String pageLevelCoordinates;
    public String inputType;

    // Signature image — file path preferred over inline base64
    public String signatureImagePath;
    public String signatureImage;
    public int signatureFontSize;

    // Flags (coSign defaults to true to match SDK default)
    public boolean borderRequired;
    public boolean coSign = true;
    public boolean rightOrigin;
    public String pdfPassword;
    public boolean patchSignatureAppearance;

    // Nested appearance objects
    public CliContentSearch contentSearch;
    public CliAdvanceSignature advanceSignature;
    public CliColoredGraphicInputs coloredGraphicInputs;
    public CliCustomStyle customStyle;

    public static class CliContentSearch {
        public String searchText;
        public int height;
        public int width;
        public String offset;
        public String position;
    }

    public static class CliAdvanceSignature {
        public String imagePath;
        public String imageBase64;
        public String imageType;
        public String leftSideText;
        public String rightSideText;
    }

    public static class CliColoredGraphicInputs {
        public int[] leftBorder;
        public int[] rightBorder;
    }

    public static class CliCustomStyle {
        public String border;
        public String fontSize;
        public CliColor fontColor;
    }

    public static class CliColor {
        public int r;
        public int g;
        public int b;
    }
}
