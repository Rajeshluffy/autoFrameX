package com.alfa3DViewer.testcases;

import org.testng.annotations.Test;

import com.alfa3DViewer.pages.AssemblyTree;
import com.alfaDOCK.testcases.AlfaDockBaseTest;
import com.framework.testng.api.base.TestMetadata;

@TestMetadata(
        name = "Assembly Tree",
        description = "Verify user login and search file functionality in Drawing Manager",
        authors = "Rajesh",
        category = "Smoke"
)
public class TC0001_OpenAssemblyTree extends AlfaDockBaseTest {

    @Test
    public void OpenAssemblyTree() {
    	drinst();
        openFileInViewer("a3dasm");
        new AssemblyTree()
                .selectAssemblyTree()
                .selectParentComponent();
    }
}
