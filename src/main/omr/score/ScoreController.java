//----------------------------------------------------------------------------//
//                                                                            //
//                       S c o r e C o n t r o l l e r                        //
//                                                                            //
//  Copyright (C) Herve Bitteur 2000-2007. All rights reserved.               //
//  This software is released under the GNU General Public License.           //
//  Contact author at herve.bitteur@laposte.net to report bugs & suggestions. //
//----------------------------------------------------------------------------//
//
package omr.score;

import omr.selection.Selection;
import omr.selection.SelectionHint;
import omr.selection.SelectionObserver;
import omr.selection.SelectionTag;

import omr.sheet.Sheet;
import omr.sheet.SheetManager;

import omr.ui.icon.IconManager;
import omr.ui.util.SwingWorker;
import static omr.ui.util.UIUtilities.*;

import omr.util.Implement;
import omr.util.Logger;

import java.awt.event.*;
import java.io.*;
import java.util.*;
import java.util.List;

import javax.swing.*;
import javax.swing.event.*;

/**
 * Class <code>ScoreController</code> encapsulates a set of user interface means
 * on top of ScoreManager, related to score handling actions, typically 
 * triggered through menus and buttons.
 *
 * @author Herv&eacute; Bitteur
 * @version $Id$
 */
public class ScoreController
    implements SelectionObserver
{
    //~ Static fields/initializers ---------------------------------------------

    /** Usual logger utility */
    private static final Logger logger = Logger.getLogger(
        ScoreController.class);

    //~ Instance fields --------------------------------------------------------

    /** Menu for score actions */
    private final JMenu scoreMenu = new JMenu("Score");

    /** Toolbar needed to add buttons in it */
    private final JToolBar toolBar;

    /** Collection of score-dependent actions, that are enabled only if there is
       a current score. */
    private final List<Object> scoreDependentActions = new ArrayList<Object>();

    //~ Constructors -----------------------------------------------------------

    //-----------------//
    // ScoreController //
    //-----------------//
    /**
     * Create the Controller artifacts.
     *
     * @param toolBar the (global) tool bar to use
     */
    public ScoreController (JToolBar toolBar)
    {
        this.toolBar = toolBar;
        scoreMenu.setToolTipText("Select action for current score");

        // Display Actions
        new BrowseAction();
        new DumpAction();
        scoreMenu.addSeparator();

        // XML Actions
        new StoreAction();

        // Initially disabled actions
        enableActions(scoreDependentActions, false);

        // Stay informed on sheet selection
        SheetManager.getSelection()
                    .addObserver(this);
    }

    //~ Methods ----------------------------------------------------------------

    //------------//
    // setEnabled //
    //------------//
    /**
     * Allow to enable or disable this whole menu
     * @param bool true to enable, false to disable
     */
    public void setEnabled (boolean bool)
    {
        scoreMenu.setEnabled(bool);
    }

    //-----------------//
    // getCurrentScore //
    //-----------------//
    /**
     * Report the current selected Score (the score related to the currently
     * selected sheet)
     *
     * @return the current score, or null if none selected
     */
    public static Score getCurrentScore ()
    {
        Sheet sheet = SheetManager.getSelectedSheet();

        if (sheet != null) {
            return sheet.getScore();
        }

        return null;
    }

    //---------//
    // getMenu //
    //---------//
    /**
     * This method is meant for access by MainGui (in the same package), to report
     * the menu dedicated to score handling.
     *
     * @return the score menu
     */
    public JMenu getMenu ()
    {
        return scoreMenu;
    }

    //---------//
    // getName //
    //---------//
    @Implement(SelectionObserver.class)
    public String getName ()
    {
        return "ScoreController";
    }

    //--------------//
    // setScoreView //
    //--------------//
    /**
     * Set the various display parameter of a view for the given score, if such
     * score is provided, or a blank tab otherwise.
     *
     * @param score the desired score if any, null otherwise
     */
    public void setScoreView (Score score)
    {
        if (score != null) {
            // Make sure we have a proper score view
            ScoreView view = score.getView();

            if (view == null) {
                // Build a brand new display on this score
                view = new ScoreView(score);
            } else {
                // So that scroll bars be OK
                view.computeModelSize();
            }

            // Make sure the view is part of the related sheet assembly
            Sheet sheet = score.getSheet();
            sheet.getAssembly()
                 .setScoreView(view);
        }

        enableActions(scoreDependentActions, score != null);
    }

    //--------//
    // update //
    //--------//
    @Implement(SelectionObserver.class)
    public void update (Selection     selection,
                        SelectionHint hint)
    {
        if (selection.getTag() == SelectionTag.SHEET) {
            Sheet sheet = (Sheet) selection.getEntity();
            enableActions(
                scoreDependentActions,
                (sheet != null) && (sheet.getScore() != null));
        }
    }

    //~ Inner Classes ----------------------------------------------------------

    //--------------//
    // BrowseAction //
    //--------------//
    /**
     * Class <code>BrowseAction</code> launches the tree display of the current
     * score.
     */
    private class BrowseAction
        extends ScoreAction
    {
        public BrowseAction ()
        {
            super(
                false,
                "Browse Score",
                "Browse through the score document",
                IconManager.getInstance().loadImageIcon("general/Search"));
        }

        @Override
        public void actionPerformed (ActionEvent e)
        {
            getCurrentScore()
                .viewScore();
        }
    }

    //------------//
    // DumpAction //
    //------------//
    private class DumpAction
        extends ScoreAction
    {
        public DumpAction ()
        {
            super(
                false,
                "Dump Score",
                "Dump the current score",
                IconManager.getInstance().loadImageIcon("general/PrintPreview"));
        }

        @Override
        public void actionPerformed (ActionEvent e)
        {
            getCurrentScore()
                .dump();
        }
    }

    //-------------//
    // ScoreAction //
    //-------------//
    /**
     * Class <code>ScoreAction</code> is a template for any score-related
     * action: it builds the action, registers it in the list of score-dependent
     * actions if needed, inserts the action in the score menu, and inserts a
     * button in the toolbar if an icon is provided.
     */
    private abstract class ScoreAction
        extends AbstractAction
    {
        public ScoreAction (boolean enabled,
                            String  label,
                            String  tip,
                            Icon    icon)
        {
            super(label, icon);
            putValue(SHORT_DESCRIPTION, tip);

            // Is this a Score-dependent action ? If so, it is by default
            // disabled, so we use this characteristic to detect such actions
            if (!enabled) {
                scoreDependentActions.add(this);
            }

            // Add the related Menu item
            scoreMenu.add(this);

            // Add an icon in the Tool bar, if any icon is provided
            if (icon != null) {
                final JButton button = toolBar.add(this);
                button.setBorder(getToolBorder());
            }
        }
    }

    //-------------//
    // StoreAction //
    //-------------//
    /**
     * Class <code>StoreAction</code> handles the saving of the currently
     * selected score, using a binary or xml format.
     */
    private class StoreAction
        extends ScoreAction
    {
        public StoreAction ()
        {
            super(false, 
                "Store in XML",
                "Store current score in MusicXML",
                IconManager.getInstance().loadImageIcon("general/Export"));
        }

        @Override
        public void actionPerformed (ActionEvent e)
        {
            final SwingWorker worker = new SwingWorker() {
                @Override
                public Object construct ()
                {
                    Score score = getCurrentScore();

                    try {
                        score.export();
                    } catch (Exception ex) {
                        logger.warning("Could not store " + score, ex);
                    }

                    return null;
                }
            };

            worker.start();
        }
    }
}
