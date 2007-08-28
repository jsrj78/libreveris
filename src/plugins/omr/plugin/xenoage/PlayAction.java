//----------------------------------------------------------------------------//
//                                                                            //
//                            P l a y A c t i o n                             //
//                                                                            //
//  Copyright (C) Brenton Partridge 2007. All rights reserved.                //
//  This software is released under the GNU General Public License.           //
//  Contact author at herve.bitteur@laposte.net to report bugs & suggestions. //
//----------------------------------------------------------------------------//
package omr.plugin.xenoage;

import omr.plugin.Plugin;
import omr.plugin.PluginType;

import omr.score.Score;
import omr.score.visitor.ScoreExporter;

import omr.sheet.Sheet;
import omr.sheet.SheetManager;

import omr.ui.icon.IconManager;

import omr.util.Implement;
import omr.util.Logger;

import com.xenoage.player.ExternalPlayer;

import java.awt.event.*;
import java.io.*;

import javax.swing.*;

/**
 * Class <code>PlayAction</code> is a MIDI player plugin, based on Xenoage
 * Player as described on http://www.xenoage.com/player/
 *
 * For the time being, it simply passes the whole score information to the
 * player.
 *
 * @author Brenton Partridge
 * @author Herv&eacute Bitteur
 * @version $Id$
 */
@Plugin(type = PluginType.SCORE_EXPORT)
public class PlayAction
    extends AbstractAction
{
    //~ Static fields/initializers ---------------------------------------------

    /** Usual logger utility */
    private static final Logger logger = Logger.getLogger(PlayAction.class);

    //~ Constructors -----------------------------------------------------------

    //------------//
    // PlayAction //
    //------------//
    /**
     * Create the Play action, as a score-dependent action
     */
    public PlayAction ()
    {
        super("Play");
        putValue(
            SMALL_ICON,
            IconManager.getInstance().loadImageIcon("media/Play"));
        putValue(SHORT_DESCRIPTION, "Play score as MIDI using Xenoage Player");
        setEnabled(false); // It is a score-dependent action
    }

    //~ Methods ----------------------------------------------------------------

    //-----------------//
    // actionPerformed //
    //-----------------//
    @Implement(ActionListener.class)
    public void actionPerformed (ActionEvent e)
    {
        Sheet sheet = SheetManager.getSelectedSheet();

        if (sheet != null) {
            Score score = sheet.getScore();

            if (score != null) {
                try {
                    // A pair of connected piped streams
                    final PipedInputStream pis = new PipedInputStream();
                    PipedOutputStream      pos = new PipedOutputStream(pis);

                    // Launch the reading/playing asynchronously
                    final Runnable runnable = new Runnable() {
                        public void run ()
                        {
                            try {
                                // Get access to a Xenoage player
                                ExternalPlayer player = new ExternalPlayer(
                                    null);
                                // Read the piped input stream
                                player.setMusicXMLData(pis);
                                logger.info("Playing...");
                                player.play();
                            } catch (Exception ex) {
                                logger.warning("Player error", ex);
                            }
                        }
                    };

                    // Start the reader
                    Thread t = new Thread(runnable);
                    t.start();

                    // Write the score XML data to the piped output stream
                    new ScoreExporter(score, pos);

                    // Close the stream, to notify the end to the reader
                    pos.close();
                } catch (Exception ex) {
                    logger.warning("Player error", ex);
                }
            }
        }
    }
}
