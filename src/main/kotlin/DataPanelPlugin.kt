import com.lambda.client.plugin.api.Plugin

internal object DataPanelPlugin: Plugin() {

    override fun onLoad() {
        modules.add(DataPanel)
    }

    override fun onUnload() {
        if (DataPanel.window != null) {
            DataPanel.window!!.isVisible = false
            DataPanel.window = null
        }
    }
}