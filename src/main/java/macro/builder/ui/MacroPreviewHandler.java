package macro.builder.ui;

public interface MacroPreviewHandler {
    void preview(String macroContent) throws Exception;
    void cleanup();
}
