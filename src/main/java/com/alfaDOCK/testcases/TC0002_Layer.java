package com.alfaDOCK.testcases;

import org.testng.annotations.Test;

import com.alfa3DViewer.pages.Layers;
import com.alfa3DViewer.pages.Loading;
import com.framework.testng.api.base.TestMetadata;

@TestMetadata(name = "Layer", description = "Verify the Layer Command", authors = "Rajesh", category = "Smoke")
public class TC0002_Layer extends AlfaDockBaseTest {

	/** Verify model layer is enabled by default when the viewer opens. */
	@Test
	public void TC0001_ModelLayer() {
		drinst();
		openFileInViewer("a3dasm");
		new Layers()
		.selectLayers()
		.isModelLayerEnabled();
	}

	/** Verify model layer state is not preserved after a page refresh. */
	@Test
	public void TC0002_ModelLayer() {
		drinst();
		openFileInViewer("a3dasm");
		new Layers()
		.selectLayers()
		.selectModelLayer();
		refresh();
		new Loading().loadingDisappear();
		new Layers()
		.isModelLayerEnabled();
	}
}
