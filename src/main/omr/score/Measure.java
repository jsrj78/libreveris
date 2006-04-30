//-----------------------------------------------------------------------//
//                                                                       //
//                             M e a s u r e                             //
//                                                                       //
//  Copyright (C) Herve Bitteur 2000-2006. All rights reserved.          //
//  This software is released under the terms of the GNU General Public  //
//  License. Please contact the author at herve.bitteur@laposte.net      //
//  to report bugs & suggestions.                                        //
//-----------------------------------------------------------------------//

package omr.score;

import omr.glyph.Shape;
import omr.lag.Lag;
import omr.sheet.BarInfo;
import omr.ui.icon.SymbolIcon;
import omr.ui.view.Zoom;
import omr.util.Dumper;
import omr.util.Logger;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import javax.swing.Icon;

/**
 * Class <code>Measure</code> handles a measure of a staff.
 *
 * @author Herv&eacute; Bitteur
 * @version $Id$
 */
public class Measure
    extends StaffNode
{
    //~ Static variables/initializers -------------------------------------

    private static final Logger logger = Logger.getLogger(Measure.class);

    //~ Instance variables ------------------------------------------------

    // Related infos from sheet analysis
    private List<BarInfo> infos = new ArrayList<BarInfo>(2);

    // Attributes
    private Shape linetype;
    private int leftlinex;
    private int rightlinex;
    private boolean lineinvented;
    private int id = 0; // Measure Id
    private int leftX; // X of start of this measure (wrt staff)

    //~ Constructors ------------------------------------------------------

    //---------//
    // Measure //
    //---------//
    /**
     * Default constructor (needed by XML Binder)
     */
    public Measure ()
    {
        super(null, null);
    }

    //---------//
    // Measure //
    //---------//
    /**
     * Create a measure with the specified parameters
     *
     * @param info         physical description of the ending bar line
     * @param staff        the containing staff
     * @param linetype     the kind of ending bar line
     * @param leftlinex    abscissa of the left part of the ending bar line
     * @param rightlinex   abscissa of the right part of the ending bar line
     * @param lineinvented flag an artificial ending bar line if none existed
     */
    public Measure (BarInfo info,
                    Staff staff,
                    Shape linetype,
                    int leftlinex,
                    int rightlinex,
                    boolean lineinvented)
    {
        super(staff, staff);

        this.infos.add(info);
        this.linetype = linetype;
        this.leftlinex = leftlinex;
        this.rightlinex = rightlinex;
        this.lineinvented = lineinvented;

        if (logger.isFineEnabled()) {
            Dumper.dump(this, "Constructed");
        }
    }

    //~ Methods -----------------------------------------------------------

    //----------//
    // getInfos //
    //----------//
    /**
     * Report the BarInfo list related to the ending bar line(s)
     *
     * @return the BarInfo list
     */
    public List<BarInfo> getInfos ()
    {
        return infos;
    }

    //--------------//
    // setLeftlinex //
    //--------------//
    /**
     * Set the abscissa of the left part of the ending bar line
     *
     * @param leftlinex the abscissa (in units)
     */
    public void setLeftlinex (int leftlinex)
    {
        this.leftlinex = leftlinex;
    }

    //--------------//
    // getLeftlinex //
    //--------------//
    /**
     * Report the abscissa of the left part of the ending bar line
     *
     * @return the abscissa (in units)
     */
    public int getLeftlinex ()
    {
        return leftlinex;
    }

    //-------------//
    // setLinetype //
    //-------------//
    /**
     * Set the line type of the ending bar line
     *
     * @param linetype the line type, as the proper enumerated type
     */
    public void setLinetype (Shape linetype)
    {
        this.linetype = linetype;
    }

    //-------------//
    // setLinetype //
    //-------------//
    /**
     * Set the line type of the ending bar line
     *
     * @param linetype the line type, as a string
     */
    public void setLinetype (String linetype)
    {
        setLinetype(Shape.valueOf(linetype));
    }

    //-------------//
    // getLinetype //
    //-------------//
    /**
     * Report the type of the ending bar line
     *
     * @return the enumerated line type
     */
    public String getLinetype ()
    {
        return linetype.toString();
    }

    //---------------//
    // setRightlinex //
    //---------------//
    /**
     * Set the abscissa of the right part of the ending bar line
     *
     * @param rightlinex the abscissa (in units)
     */
    public void setRightlinex (int rightlinex)
    {
        this.rightlinex = rightlinex;
    }

    //---------------//
    // getRightlinex //
    //---------------//
    /**
     * Report the abscissa of the right part of the ending bar line
     *
     * @return the abscissa (in units)
     */
    public int getRightlinex ()
    {
        return rightlinex;
    }

    //----------//
    // addInfos //
    //----------//
    /**
     * Merge the provided barinfos with existing one
     *
     * @param list list of bar info objects
     */
    public void addInfos (List<BarInfo> list)
    {
        this.infos.addAll(list);
    }

    //--------------//
    // colorizeNode //
    //--------------//
    /**
     * Colorize the physical information of this measure
     *
     * @param lag       the lag to be colorized
     * @param viewIndex the provided lag view index
     * @param color     the color to be used
     *
     * @return true if processing must continue
     */
    @Override
    protected boolean colorizeNode (Lag lag,
                                    int viewIndex,
                                    Color color)
    {
        // Set color for the sections of the ending bars
        for (BarInfo bar : infos) {
            bar.colorize(lag, viewIndex, color);
        }

        return true;
    }

    //-------------//
    // computeNode //
    //-------------//
    /**
     * Overriding definition, so that computations specific to a measure
     * are performed
     *
     * @return true, so that processing continues
     */
    @Override
        protected boolean computeNode ()
    {
        super.computeNode();

        // Fix the staff reference
        setStaff((Staff) container.getContainer());

        // First/Last measure ids
        staff.incrementLastMeasureId();
        id = staff.getLastMeasureId();

        // Start of the measure
        Measure prevMeasure = (Measure) getPreviousSibling();

        if (prevMeasure == null) { // Very first measure in the staff
            leftX = 0;
        } else {
            leftX = prevMeasure.rightlinex;
        }

        return true;
    }

    //-----------//
    // paintNode //
    //-----------//
    @Override
        protected boolean paintNode (Graphics g,
                                     Zoom zoom,
                                     Component comp)
    {
        Point origin = getOrigin();

        // Draw the bar line symbol at the end of the measure
        SymbolIcon icon = (SymbolIcon) linetype.getIcon();
        if (icon == null) {
            logger.warning("Need icon for " + linetype);
        } else {
            icon.paintIcon
                (comp,
                 g,
                 zoom.scaled(origin.x + (leftlinex + rightlinex) / 2)
                 - icon.getActualWidth()/2,
                 zoom.scaled(origin.y));
        }

        // Draw the measure id, if on the first staff only
        if (staff.getStafflink() == 0) {
            g.setColor(Color.lightGray);
            g.drawString(Integer.toString(id),
                         zoom.scaled(origin.x + leftX) - 5,
                         zoom.scaled(origin.y) - 15);
        }

        return true;
    }

    //------------//
    // renderNode //
    //------------//
    /**
     * Render the physical information of this measure
     *
     * @param g the graphics context
     * @param z the display zoom
     *
     * @return true if rendered
     */
    @Override
    protected boolean renderNode (Graphics g,
                                  Zoom z)
    {
        for (BarInfo bar : infos) {
            bar.render(g, z);
        }

        return true;
    }
}
