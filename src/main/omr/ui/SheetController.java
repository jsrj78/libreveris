//----------------------------------------------------------------------------//
//                                                                            //
//                       S h e e t C o n t r o l l e r                        //
//                                                                            //
//  Copyright (C) Herve Bitteur 2000-2007. All rights reserved.               //
//  This software is released under the GNU General Public License.           //
//  Contact author at herve.bitteur@laposte.net to report bugs & suggestions. //
//----------------------------------------------------------------------------//
//
package omr.ui;

import omr.Main;

import omr.constant.Constant;
import omr.constant.ConstantSet;

import omr.glyph.ui.GlyphRepository;

import omr.score.Score;

import omr.script.ScriptController;

import omr.selection.Selection;

import omr.sheet.*;

import omr.step.Step;

import omr.ui.MainGui;
import omr.ui.SheetAssembly;
import omr.ui.icon.IconManager;
import omr.ui.util.FileFilter;
import omr.ui.util.SwingWorker;
import omr.ui.util.UIUtilities;

import omr.util.Implement;
import omr.util.Logger;

import java.awt.event.*;
import java.io.*;
import java.util.*;

import javax.swing.*;
import javax.swing.event.*;

/**
 * Class <code>SheetController</code> encapsulates the display of (possibly
 * several) sheet(s).
 *
 * <p>Multiple sheets are handled by means of a tabbed pane. For each tab, and
 * thus for each sheet, we have a separate {@link SheetAssembly}.
 *
 * <p>This class is meant to be a Singleton
 *
 * <dl>
 * <dt><b>Selection Outputs:</b></dt><ul>
 * <li>SHEET
 * </ul>
 * </dl>
 *
 * @author Herv&eacute; Bitteur
 * @version $Id$
 */
public class SheetController
    implements ChangeListener
{
    //~ Static fields/initializers ---------------------------------------------

    /** Specific application parameters */
    private static final Constants constants = new Constants();

    /** Usual logger utility */
    private static final Logger logger = Logger.getLogger(
        SheetController.class);

    /** Flag a tab sheetIndex that is not yet available */
    public static final int DIFFERED_INDEX = -2;

    //~ Instance fields --------------------------------------------------------

    /** Ordered list of sheet assemblies */
    private final ArrayList<SheetAssembly> assemblies;

    /** Collection of sheet-dependent actions */
    private final Collection<Action> sheetDependentActions = new ArrayList<Action>();

    /** Menu dedicated to sheet-related actions */
    private final JMenu menu = new JMenu("File");

    /** The concrete tabbed pane, one tab per sheet */
    private final JTabbedPane component;

    /** Ref of toolbar where buttons are inserted */
    private final JToolBar toolBar;

    /** Should we synchronize the (score) pane on the other side ? */
    private volatile boolean synchroWanted = true;

    /** Index of previously selected sheet tab */
    private int previousSheetIndex = -1;

    //~ Constructors -----------------------------------------------------------

    //-----------------//
    // SheetController //
    //-----------------//
    /**
     * Create the SheetController, within the gui frame.
     *
     * @param gui     the enclosing gui entity
     * @param toolBar the gui tool bar
     */
    public SheetController (MainGui  gui,
                            JToolBar toolBar)
    {
        menu.setToolTipText("Sheet or script file selection");
        component = new JTabbedPane();
        assemblies = new ArrayList<SheetAssembly>();

        this.toolBar = toolBar;

        toolBar.addSeparator();

        // History menu
        JMenuItem historyMenu = SheetManager.getInstance()
                                            .getHistory()
                                            .menu(
            "Sheet History",
            new HistoryListener());
        historyMenu.setToolTipText("List of previous sheet files");
        historyMenu.setIcon(
            IconManager.getInstance().loadImageIcon("general/History"));
        menu.add(historyMenu);

        // Various actions
        new SelectSheetAction();

        // Script actions
        menu.addSeparator();

        ScriptController scriptController = new ScriptController();
        menu.add(new JMenuItem(scriptController.getOpenAction()));
        menu.add(new JMenuItem(scriptController.getStoreAction()));
        sheetDependentActions.add(scriptController.getStoreAction());

        menu.addSeparator();
        new ZoomWidthAction();
        new ZoomHeightAction();
        new RecordAction();

        // Tool actions
        menu.addSeparator();

        new ScalePlotAction();
        new SkewPlotAction();
        new LinePlotAction();

        menu.addSeparator();
        new CloseAction();

        // Initially disabled actions
        UIUtilities.enableActions(sheetDependentActions, false);

        // Listener on sheet tab operations
        component.addChangeListener(this);
    }

    //~ Methods ----------------------------------------------------------------

    //-------//
    // close //
    //-------//
    /**
     * Remove the specified view from the tabbed pane
     *
     * @param assembly the sheet assembly to close
     */
    public void close (SheetAssembly assembly)
    {
        if (logger.isFineEnabled()) {
            logger.fine("closing " + assembly.toString());
        }

        // Forget about this sheet assembly
        previousSheetIndex = -1;

        // Disable sheet-based menu actions
        UIUtilities.enableActions(sheetDependentActions, false);

        int sheetIndex = component.indexOfComponent(assembly.getComponent());

        if (sheetIndex != -1) {
            // Remove from assemblies
            assemblies.remove(sheetIndex);
            // Remove from tabs
            component.remove(sheetIndex);
        }

        if (logger.isFineEnabled()) {
            logger.fine(
                "closed " + assembly.toString() + " assemblies=" + assemblies);
        }
    }

    //--------------//
    // getComponent //
    //--------------//
    /**
     * Give access to the real pane
     *
     * @return the concrete component
     */
    public JComponent getComponent ()
    {
        return component;
    }

    //-----------------//
    // getCurrentSheet //
    //-----------------//
    /**
     * Report the currently processed sheet
     *
     * @return the current sheet, or null otherwise
     */
    public Sheet getCurrentSheet ()
    {
        SheetAssembly assembly = getSelectedAssembly();

        if (assembly != null) {
            return assembly.getSheet();
        } else {
            return null;
        }
    }

    //---------//
    // getMenu //
    //---------//
    /**
     * Report the menu dedicated to sheet handling.
     *
     * @return the sheet menu
     */
    public JMenu getMenu ()
    {
        return menu;
    }

    //------------------//
    // setSheetAssembly //
    //------------------//
    /**
     * Prepare the parameters of the assembly that relates to the specified
     * sheet.  This method is called by setScoreSheetView() above for an
     * immediate preparation and by Sheet constructor for a differed preparation
     * (when the sheet has finished loading).
     *
     * @param sheet the sheet to be viewed. If no sheet is provided, then an
     *              empty assembly is desired.
     *
     * @return the sheetIndex in the tabbed pane of the prepared assembly
     */
    public int setSheetAssembly (Sheet sheet)
    {
        if (logger.isFineEnabled()) {
            logger.fine("setSheetAssembly for sheet " + sheet.getRadix());
        }

        if (sheet != null) {
            // Make sure that scale and skew info is available for the sheet
            Score score = sheet.getScore();

            if (score != null) {
                sheet.checkScaleAndSkew(score);
            }

            // Make sure we have a assembly on this sheet
            SheetAssembly assembly = sheet.getAssembly();

            if (assembly == null) {
                // Build a brand new display on this sheet
                assembly = new SheetAssembly(sheet);

                // Initial zoom ratio
                assembly.setZoomRatio(constants.initialZoomRatio.getValue());
            }

            // Make sure the assembly is part of the tabbed pane
            int sheetIndex = component.indexOfComponent(
                assembly.getComponent());

            if (sheetIndex == -1) {
                if (logger.isFineEnabled()) {
                    logger.fine(
                        "Adding assembly for sheet " + sheet.getRadix());
                }

                // Insert in tabbed pane
                assemblies.add(assembly);
                component.addTab(
                    sheet.getRadix(),
                    null,
                    assembly.getComponent(),
                    sheet.getPath());
                sheetIndex = component.indexOfComponent(
                    assembly.getComponent());
            }

            return sheetIndex;
        } else {
            return -1; // Index of the empty tab
        }
    }

    //---------------//
    // showSheetView //
    //---------------//
    /**
     * Make the (preset) SheetView actually visible.
     *
     * @param sheetIndex the index to the SheetView to be made current, with its
     *                potential focus point, or -1 to show a blank panel.
     * @param synchro specify whether other side is to be shown also
     */
    public void showSheetView (int     sheetIndex,
                               boolean synchro)
    {
        setSynchroWanted(synchro);
        component.setSelectedIndex(sheetIndex);
        setSynchroWanted(true);
    }

    //--------------//
    // stateChanged //
    //--------------//
    /**
     * This method is called whenever the sheet selection is modified, whether
     * it's programmatically (by means of setSheetView) of by user action
     * (manual selection of the sheet tab).
     *
     * <p> Set the state (enabled or disabled) of all menu items that depend on
     * status of current sheet.
     */
    @Implement(ChangeListener.class)
    public void stateChanged (ChangeEvent e)
    {
        final Object source = e.getSource();

        if (source == component) {
            // Finalize previous sheet tab?
            if (previousSheetIndex != -1) {
                ////sheetTabDeselected(previousSheetIndex);
            }

            final int sheetIndex = component.getSelectedIndex();

            // User has selected a new sheet tab?
            if (sheetIndex != -1) {
                // Connect the new sheet tab
                sheetTabSelected(sheetIndex);
            }

            previousSheetIndex = sheetIndex;
        } else {
            logger.warning("Unexpected event from " + source);
        }
    }

    //---------------------//
    // getSelectedAssembly //
    //---------------------//
    /**
     * Report the current sheet assembly
     *
     * @return the sheet assembly currently selected
     */
    private SheetAssembly getSelectedAssembly ()
    {
        int sheetIndex = component.getSelectedIndex();

        if (sheetIndex != -1) {
            return assemblies.get(sheetIndex);
        } else {
            return null;
        }
    }

    //-----------------//
    // isSynchroWanted //
    //-----------------//
    /**
     * Check if synchronization of the other side view (score) is wanted. This
     * is usually true, except in specific case, where the initial order already
     * comes from the other side, so there is no need to go back there.
     *
     * @return the flag value
     */
    private boolean isSynchroWanted ()
    {
        return synchroWanted;
    }

    //-------------//
    // selectSheet //
    //-------------//
    /**
     * User dialog, to allow the selection and load of a sheet image file.
     */
    private void selectSheet ()
    {
        // Let the user select a sheet file
        final JFileChooser fc = new JFileChooser(
            constants.initImgDir.getValue());
        fc.addChoosableFileFilter(
            new FileFilter(
                "Major image files",
                new String[] { ".bmp", ".gif", ".jpg", ".png", ".tif" }));

        if (fc.showOpenDialog(component) == JFileChooser.APPROVE_OPTION) {
            final File file = fc.getSelectedFile();

            if (file.exists()) {
                // Register that as a user target
                try {
                    Main.getGui()
                        .setTarget(file.getCanonicalPath());
                } catch (IOException ex) {
                    ex.printStackTrace();
                }

                // Actually load the sheet picture
                Step.LOAD.performParallel(null, file);

                // Remember (even across runs) the parent directory
                constants.initImgDir.setValue(file.getParent());
            } else {
                logger.warning("File not found " + file);
            }
        }
    }

    //------------------//
    // setSynchroWanted //
    //------------------//
    /**
     * Allow to register the need (or lack of) for synchronization of the other
     * side (score view).
     *
     * @param synchroWanted the value to set to the flag
     */
    private void setSynchroWanted (boolean synchroWanted)
    {
        this.synchroWanted = synchroWanted;
    }

    //    //--------------------//
    //    // sheetTabDeselected //
    //    //--------------------//
    //    private void sheetTabDeselected (int previousIndex)
    //    {
    //        logger.info(
    //            "SheetController: sheetTabDeselected previousIndex=" +
    //            previousIndex);
    //
    //        SheetAssembly prevAssembly = assemblies.get(previousIndex);
    //        prevAssembly.assemblyDeselected();
    //    }

    //------------------//
    // sheetTabSelected //
    //------------------//
    private void sheetTabSelected (int sheetIndex)
    {
        // Remember the new selected sheet
        SheetAssembly assembly = assemblies.get(sheetIndex);
        Sheet         sheet = assembly.getSheet();

        if (logger.isFineEnabled()) {
            logger.fine(
                "SheetController: sheetTabSelected sheetIndex=" + sheetIndex +
                " sheet=" + sheet);
        }

        // Tell everyone about the new selected sheet
        Selection sheetSelection = SheetManager.getSelection();
        sheetSelection.setEntity(sheet, null);

        // Enable sheet-based menu actions ?
        UIUtilities.enableActions(sheetDependentActions, true);

        // Tell the selected assembly that it now has the focus...
        assembly.assemblySelected();
    }

    //~ Inner Classes ----------------------------------------------------------

    //-----------//
    // Constants //
    //-----------//
    private static final class Constants
        extends ConstantSet
    {
        /** Default directory for selection of image files */
        Constant.String initImgDir = new Constant.String(
            "c:/",
            "Default directory for selection of image files");

        /** Initial zoom ratio for displayed sheet pictures */
        Constant.Ratio initialZoomRatio = new Constant.Ratio(
            1d,
            "Initial zoom ratio for displayed sheet pictures");
    }

    //-----------------//
    // HistoryListener //
    //-----------------//
    /**
     * Class <code>HistoryListener</code> is used to reload a sheet file, when
     * selected from the history of previous sheets.
     */
    private static class HistoryListener
        implements ActionListener
    {
        @Implement(ActionListener.class)
        public void actionPerformed (ActionEvent e)
        {
            String fileName = e.getActionCommand();

            if (logger.isFineEnabled()) {
                logger.fine("HistoryListener for " + fileName);
            }

            Main.getGui()
                .setTarget(fileName);
            Step.LOAD.performParallel(null, new File(fileName));

            if (logger.isFineEnabled()) {
                logger.fine("End of HistoryListener");
            }

            // Other UI actions will be triggered when the sheet has finished
            // loading
        }
    }

    //-------------//
    // SheetAction //
    //-------------//
    /**
     * Class <code>SheetAction</code> is a template for any sheet-related action
     * : it builds the action, registers it in the list of sheet-dependent
     * actions if needed, inserts the action in the sheet menu, and inserts a
     * button in the toolbar if an icon is provided.
     */
    private abstract class SheetAction
        extends AbstractAction
    {
        public SheetAction (boolean enabled,
                            String  label,
                            String  tip,
                            Icon    icon,
                            boolean onToolBar)
        {
            super(label, icon);

            // Sheet-dependent action ?
            if (!enabled) {
                sheetDependentActions.add(this);
            }

            // Menu item
            menu.add(this)
                .setToolTipText(tip);

            // Tool bar
            if (onToolBar) {
                final JButton button = toolBar.add(this);
                button.setBorder(UIUtilities.getToolBorder());
                button.setToolTipText(tip);
            }
        }
    }

    //-------------//
    // CloseAction //
    //-------------//
    /**
     * Class <code>CloseAction</code> handles the closing of the currently
     * selected sheet.
     */
    private class CloseAction
        extends SheetAction
    {
        public CloseAction ()
        {
            super(
                false,
                "Close Sheet",
                "Close the current sheet",
                IconManager.getInstance().loadImageIcon("general/Remove"),
                true);
        }

        @Implement(ActionListener.class)
        public void actionPerformed (ActionEvent e)
        {
            Sheet sheet = getCurrentSheet();

            if (sheet != null) {
                Score score = sheet.getScore();

                if (score != null) {
                    score.close();
                }

                sheet.close();
            }
        }
    }

    //----------------//
    // LinePlotAction //
    //----------------//
    /**
     * Class <code>LinePlotAction</code> allows to display the plot of Line
     * Builder.
     */
    private class LinePlotAction
        extends SheetAction
    {
        public LinePlotAction ()
        {
            super(
                false,
                "Line Plot",
                "Display chart from Line builder",
                IconManager.getInstance().loadImageIcon("general/PrintPreview"),
                false);
        }

        @Implement(ActionListener.class)
        public void actionPerformed (ActionEvent e)
        {
            Sheet sheet = getCurrentSheet();

            if (sheet != null) {
                if (sheet.getLinesBuilder() != null) {
                    sheet.getLinesBuilder()
                         .displayChart();
                } else {
                    logger.warning(
                        "Data from staff line builder" + " is not available");
                }
            }
        }
    }

    //--------------//
    // RecordAction //
    //--------------//
    private class RecordAction
        extends SheetAction
    {
        public RecordAction ()
        {
            super(
                false,
                "Record Glyphs",
                "Record sheet glyph descriptions for training",
                IconManager.getInstance().loadImageIcon("general/Bookmarks"),
                false);
        }

        public void actionPerformed (ActionEvent e)
        {
            int answer = JOptionPane.showConfirmDialog(
                component,
                "Are you sure of all the symbols of this sheet ?");

            if (answer != JOptionPane.YES_OPTION) {
                return;
            }

            final SwingWorker worker = new SwingWorker() {
                public Object construct ()
                {
                    Sheet sheet = getCurrentSheet();
                    GlyphRepository.getInstance()
                                   .recordSheetGlyphs(
                        sheet, /* emptyStructures => */
                        sheet.isOnSymbols());

                    return null;
                }
            };

            worker.start();
        }
    }

    //-----------------//
    // ScalePlotAction //
    //-----------------//
    /**
     * Class <code>ScalePlotAction</code> allows to display the plot of Scale
     * Builder.
     */
    private class ScalePlotAction
        extends SheetAction
    {
        public ScalePlotAction ()
        {
            super(
                false,
                "Scale Plot",
                "Display chart from Scale builder",
                IconManager.getInstance().loadImageIcon("general/PrintPreview"),
                false);
        }

        @Implement(ActionListener.class)
        public void actionPerformed (ActionEvent e)
        {
            Sheet sheet = getCurrentSheet();

            if (sheet != null) {
                sheet.getScale()
                     .displayChart();
            }
        }
    }

    //-------------------//
    // SelectSheetAction //
    //-------------------//
    /**
     * Class <code>SelectSheetAction</code> let the user select a sheet file
     * interactively.
     */
    private class SelectSheetAction
        extends SheetAction
    {
        public SelectSheetAction ()
        {
            super(
                true,
                "Open Sheet",
                "Open a sheet file",
                IconManager.getInstance().loadImageIcon("general/Open"),
                true);
        }

        @Implement(ActionListener.class)
        public void actionPerformed (ActionEvent e)
        {
            selectSheet();
        }
    }

    //----------------//
    // SkewPlotAction //
    //----------------//
    /**
     * Class <code>SkewPlotAction</code> allows to display the plot of Skew
     * Builder.
     */
    private class SkewPlotAction
        extends SheetAction
    {
        public SkewPlotAction ()
        {
            super(
                false,
                "Skew Plot",
                "Display chart from Skew builder",
                IconManager.getInstance().loadImageIcon("general/PrintPreview"),
                false);
        }

        @Implement(ActionListener.class)
        public void actionPerformed (ActionEvent e)
        {
            Sheet sheet = getCurrentSheet();

            if (sheet != null) {
                if (sheet.getSkewBuilder() != null) {
                    sheet.getSkewBuilder()
                         .displayChart();
                } else {
                    logger.warning(
                        "Data from skew builder" + " is not available");
                }
            }
        }
    }

    //------------------//
    // ZoomHeightAction //
    //------------------//
    /**
     * Class <code>ZoomHeightAction</code> allows to adjust the display zoom, so
     * that the full height is shown.
     */
    private class ZoomHeightAction
        extends SheetAction
    {
        public ZoomHeightAction ()
        {
            super(
                false,
                "Height Fit",
                "Fit image to window height",
                IconManager.getInstance().loadImageIcon(
                    "general/AlignJustifyVertical"),
                true);
        }

        @Implement(ActionListener.class)
        public void actionPerformed (ActionEvent e)
        {
            SheetAssembly assembly = getSelectedAssembly();
            assembly.getSelectedView()
                    .fitHeight();
        }
    }

    //-----------------//
    // ZoomWidthAction //
    //-----------------//
    /**
     * Class <code>ZoomWidthAction</code> allows to adjust the display zoom, so
     * that the full width is shown.
     */
    private class ZoomWidthAction
        extends SheetAction
    {
        public ZoomWidthAction ()
        {
            super(
                false,
                "Width Fit",
                "Fit image to window width",
                IconManager.getInstance().loadImageIcon(
                    "general/AlignJustifyHorizontal"),
                true);
        }

        @Implement(ActionListener.class)
        public void actionPerformed (ActionEvent e)
        {
            SheetAssembly assembly = getSelectedAssembly();
            assembly.getSelectedView()
                    .fitWidth();
        }
    }
}
