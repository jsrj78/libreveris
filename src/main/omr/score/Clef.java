//----------------------------------------------------------------------------//
//                                                                            //
//                                  C l e f                                   //
//                                                                            //
//  Copyright (C) Herve Bitteur 2000-2006. All rights reserved.               //
//  This software is released under the terms of the GNU General Public       //
//  License. Please contact the author at herve.bitteur@laposte.net           //
//  to report bugs & suggestions.                                             //
//----------------------------------------------------------------------------//
//
package omr.score;

import omr.glyph.Glyph;
import omr.glyph.Shape;

import omr.score.visitor.Visitor;

import omr.util.Logger;

/**
 * Class <code>Clef</code> encapsulates a clef.
 *
 * @author Herv&eacute Bitteur
 * @version $Id$
 */
public class Clef
    extends StaffNode
{
    //~ Static fields/initializers ---------------------------------------------

    /** Usual logger utility */
    private static final Logger logger = Logger.getLogger(Clef.class);

    //~ Instance fields --------------------------------------------------------

    /** Precise clef shape, from Clefs range in Shape class */
    private Shape shape;

    /**
     * Step line of the clef : -4 for top line (Baritone), -2 for Bass, 0 for
     * Alto, +2 for Treble and Mezzo-Soprano, +4 for bottom line (Soprano).
     */
    private int pitchPosition;

    //~ Constructors -----------------------------------------------------------

    //------//
    // Clef //
    //------//
    /**
     * Create a Clef instance
     *
     * @param container the container (the measure clef list)
     * @param staff the containing staff
     * @param shape precise clef shape
     * @param center center wrt staff (in units)
     * @param pitchPosition pitch position
     */
    public Clef (MusicNode  container,
                 Staff      staff,
                 Shape      shape,
                 StaffPoint center,
                 int        pitchPosition)
    {
        super(container, staff);
        this.shape = shape;
        this.center = center;
        this.pitchPosition = pitchPosition;
    }

    //~ Methods ----------------------------------------------------------------

    //------------------//
    // getPitchPosition //
    //------------------//
    /**
     * Report the vertical position within the staff
     *
     * @return the pitch position
     */
    public int getPitchPosition ()
    {
        return pitchPosition;
    }

    //----------//
    // getShape //
    //----------//
    /**
     * Report the precise shape of this clef
     *
     * @return the clef shape
     */
    public Shape getShape ()
    {
        return shape;
    }

    //--------//
    // accept //
    //--------//
    @Override
    public boolean accept (Visitor visitor)
    {
        return visitor.visit(this);
    }

    //----------//
    // populate //
    //----------//
    static boolean populate (Glyph      glyph,
                             Measure    measure,
                             StaffPoint staffPoint)
    {
        Shape shape = glyph.getShape();

        switch (shape) {
        case G_CLEF :
        case G_CLEF_OTTAVA_ALTA :
        case G_CLEF_OTTAVA_BASSA :
            new Clef(measure, measure.getStaff(), shape, staffPoint, 2);

            return true;

        case F_CLEF :
        case F_CLEF_OTTAVA_ALTA :
        case F_CLEF_OTTAVA_BASSA :
            new Clef(measure, measure.getStaff(), shape, staffPoint, -2);

            return true;

        default :
            logger.warning("No implementation yet for " + shape);

            return false;
        }
    }
}
