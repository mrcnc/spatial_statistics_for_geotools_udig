/*
 * uDig - User Friendly Desktop Internet GIS client
 * (C) MangoSystem - www.mangosystem.com 
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * (http://www.eclipse.org/legal/epl-v10.html), and the HydroloGIS BSD
 * License v1.0 (http://udig.refractions.net/files/hsd3-v10.html).
 */
package org.locationtech.udig.processingtoolbox.tools;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.commons.io.IOUtils;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.dialogs.TitleAreaDialog;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableItem;
import org.eclipse.swt.widgets.Text;
import org.eclipse.swt.widgets.Widget;
import org.geotools.data.DataUtilities;
import org.geotools.process.spatialstatistics.core.StringHelper;
import org.geotools.styling.Style;
import org.geotools.util.logging.Logging;
import org.locationtech.udig.catalog.CatalogPlugin;
import org.locationtech.udig.catalog.ICatalog;
import org.locationtech.udig.catalog.IGeoResource;
import org.locationtech.udig.catalog.IService;
import org.locationtech.udig.processingtoolbox.ToolboxPlugin;
import org.locationtech.udig.processingtoolbox.internal.ui.OutputDataWidget;
import org.locationtech.udig.processingtoolbox.internal.ui.WidgetBuilder;
import org.locationtech.udig.processingtoolbox.styler.SSStyleBuilder;
import org.locationtech.udig.project.IMap;
import org.locationtech.udig.project.internal.Layer;
import org.locationtech.udig.project.ui.ApplicationGIS;
import org.locationtech.udig.style.sld.SLDContent;

/**
 * Abstract GeoProcessing Dialog
 * 
 * @author Minpa Lee, MangoSystem
 * 
 * @source $URL$
 */
public abstract class AbstractGeoProcessingDialog extends TitleAreaDialog {
    protected static final Logger LOGGER = Logging.getLogger(AbstractGeoProcessingDialog.class);

    protected int increment = 10; // IRunnableWithProgress
    
    protected final String EMPTY = ""; //$NON-NLS-1$
    
    protected final String DOT3 = "..."; //$NON-NLS-1$

    protected String windowTitle = EMPTY;

    protected String windowDesc = EMPTY;

    protected Point windowSize = new Point(650, 400);

    protected OutputDataWidget locationView;

    protected String error;

    protected IMap map;

    protected WidgetBuilder uiBuilder = WidgetBuilder.newInstance();

    public AbstractGeoProcessingDialog(Shell parentShell, IMap map) {
        super(parentShell);
        this.map = map;
    }

    @Override
    public void create() {
        super.create();
        setTitle(windowTitle);
        setMessage(windowDesc);
    }

    @Override
    protected Point getInitialSize() {
        return windowSize;
    }

    @Override
    protected void configureShell(Shell newShell) {
        super.configureShell(newShell);
        newShell.setText(windowTitle);
    }

    @SuppressWarnings("nls")
    protected String saveErrorAsText() {
        OutputStream os = null;
        try {
            File folder = new File(System.getProperty("user.home"));
            File file = File.createTempFile("gxt_", ".txt", folder);
            os = new FileOutputStream(file);
            Writer writer = new OutputStreamWriter(os, Charset.defaultCharset());
            writer.write(error);
            return file.getPath();
        } catch (FileNotFoundException e) {
            LOGGER.log(Level.FINER, e.getMessage(), e);
        } catch (IOException e) {
            LOGGER.log(Level.FINER, e.getMessage(), e);
        } finally {
            IOUtils.closeQuietly(os);
        }
        return null;
    }

    protected boolean existCheckedItem(Table table) {
        for (TableItem item : table.getItems()) {
            if (item.getChecked()) {
                return true;
            }
        }
        return false;
    }

    protected boolean invalidWidgetValue(Widget... widgets) {
        for (Widget widget : widgets) {
            if (widget instanceof Combo && ((Combo) widget).getText().isEmpty()) {
                return true;
            } else if (widget instanceof Text
                    && StringHelper.isNullOrEmpty(((Text) widget).getText())) {
                return true;
            } else if (widget instanceof Table && ((Table) widget).getItemCount() == 0) {
                return true;
            }
        }

        return false;
    }

    protected void openInformation(final Shell activeCell, Object msg) {
        MessageDialog.openInformation(activeCell, activeCell.getText(), msg.toString());
    }

    protected void setEnabled(boolean enabled) {
        Button okButton = getButton(IDialogConstants.OK_ID);
        if (okButton != null) {
            okButton.setEnabled(enabled);
        }
    }

    protected void setComboItems(Combo combo, Class<?> enumClass) {
        for (Object enumVal : enumClass.getEnumConstants()) {
            combo.add(enumVal.toString());
        }
    }

    protected void addFeaturesToMap(IMap map, String filePath, String layerName) {
        try {
            File shapefile = new File(filePath);
            if (!shapefile.exists()) {
                return;
            }

            CatalogPlugin catalogPlugin = CatalogPlugin.getDefault();
            ICatalog localCatalog = catalogPlugin.getLocalCatalog();

            URL resourceId = DataUtilities.fileToURL(shapefile);
            List<IService> services = catalogPlugin.getServiceFactory().createService(resourceId);
            for (IService service : services) {
                localCatalog.add(service);
                for (IGeoResource resource : service.resources(null)) {
                    List<IGeoResource> resourceList = Collections.singletonList(resource);
                    final int pos = map.getMapLayers().size();
                    Layer layer = (Layer) ApplicationGIS.addLayersToMap(map, resourceList, pos)
                            .get(0);
                    layer.setName(layerName);

                    SSStyleBuilder ssBuilder = new SSStyleBuilder(layer.getSchema());
                    ssBuilder.setOpacity(0.8f);

                    Style style = ssBuilder.getDefaultFeatureStyle();
                    if (style != null) {
                        layer.getStyleBlackboard().put(SLDContent.ID, style);
                        layer.getStyleBlackboard().setSelected(new String[] { SLDContent.ID });
                    }

                    // refresh
                    layer.refresh(layer.getBounds(new NullProgressMonitor(), null));
                }
            }
        } catch (MalformedURLException e) {
            ToolboxPlugin.log(e.getMessage());
        } catch (IOException e) {
            ToolboxPlugin.log(e.getMessage());
        }
    }

}
