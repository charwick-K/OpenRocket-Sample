package info.openrocket.core.rocketcomponent;

import static java.lang.Math.sin;
import static info.openrocket.core.util.MathUtil.pow2;
import static info.openrocket.core.util.MathUtil.pow3;

import java.util.Collection;

import info.openrocket.core.l10n.Translator;
import info.openrocket.core.preset.ComponentPreset;
import info.openrocket.core.preset.ComponentPreset.Type;
import info.openrocket.core.startup.Application;
import info.openrocket.core.util.Coordinate;
import info.openrocket.core.util.MathUtil;

public class Transition extends SymmetricComponent implements InsideColorComponent {
	private static final Translator trans = Application.getTranslator();
	private static final double CLIP_PRECISION = 0.0001;
	final static double MINFEATURE = 0.001;

	private Shape type;
	private double shapeParameter;
	private boolean clipped; // Not to be read - use isClipped(), which may be overridden

	protected double foreRadius, aftRadius;		// Warning: avoid using these directly, use getForeRadius() and getAftRadius() instead (because the definition of the two can change for flipped nose cones)
	protected boolean autoForeRadius, autoAftRadius; // Whether the start radius is automatic

	private double foreShoulderRadius;
	private double foreShoulderThickness;
	private double foreShoulderLength;
	private boolean foreShoulderCapped;
	private double aftShoulderRadius;
	private double aftShoulderThickness;
	private double aftShoulderLength;
	private boolean aftShoulderCapped;

	// Used to cache the clip length
	private double clipLength = -1;

	private InsideColorComponentHandler insideColorComponentHandler = new InsideColorComponentHandler(this);

	public Transition() {
		super();

		this.foreRadius = DEFAULT_RADIUS;
		this.aftRadius = DEFAULT_RADIUS;
		this.length = DEFAULT_RADIUS * 3;
		this.autoForeRadius = true;
		this.autoAftRadius = true;

		this.type = Shape.CONICAL;
		this.shapeParameter = 0;
		this.clipped = true;

		super.displayOrder_side = 2; // Order for displaying the component in the 2D side view
		super.displayOrder_back = 2; // Order for displaying the component in the 2D back view
	}

	//////// Length ////////
	@Override
	public void setLength(double length) {
		if (this.length == length) {
			return;
		}
		// Need to clearPreset when length changes.
		clearPreset();
		super.setLength(length);
	}

	//////// Fore radius ////////

	@Override
	public double getForeRadius() {
		if (isForeRadiusAutomatic()) {
			foreRadius = getAutoForeRadius();
		}
		return foreRadius;
	}

	/**
	 * Returns the automatic radius from the front, taken from the previous
	 * component. Returns the default radius if there
	 * is no previous component.
	 */
	protected double getAutoForeRadius() {
		SymmetricComponent c = this.getPreviousSymmetricComponent();
		if (c != null) {
			return c.getFrontAutoRadius();
		} else {
			return DEFAULT_RADIUS;
		}
	}

	/**
	 * Set the new fore radius, with option to clamp the thickness to the new radius
	 * if it's too large.
	 *
	 * @param radius     new radius
	 * @param doClamping whether to clamp the thickness
	 */
	public void setForeRadius(double radius, boolean doClamping) {
		for (RocketComponent listener : configListeners) {
			if (listener instanceof Transition) {
				((Transition) listener).setForeRadius(radius);
			}
		}

		if ((this.foreRadius == radius) && (autoForeRadius == false))
			return;

		this.autoForeRadius = false;
		this.foreRadius = Math.max(radius, 0);

		if (doClamping && this.thickness > this.foreRadius && this.thickness > this.aftRadius)
			this.thickness = Math.max(this.foreRadius, this.aftRadius);

		clearPreset();
		fireComponentChangeEvent(ComponentChangeEvent.BOTH_CHANGE);

		setForeShoulderRadius(getForeShoulderRadius(), doClamping);
	}

	public void setForeRadius(double radius) {
		setForeRadius(radius, true);
	}

	@Override
	public boolean isForeRadiusAutomatic() {
		return autoForeRadius;
	}

	/**
	 * Set the fore radius to automatic mode (takes its value from the previous
	 * symmetric component's radius).
	 *
	 * @param auto        whether to set the fore radius to automatic mode
	 * @param sanityCheck whether to sanity check auto mode for whether there is a
	 *                    previous component of which you can take the radius
	 */
	public void setForeRadiusAutomatic(boolean auto, boolean sanityCheck) {
		for (RocketComponent listener : configListeners) {
			if (listener instanceof Transition) {
				((Transition) listener).setForeRadiusAutomatic(auto);
			}
		}

		// You can only set the auto fore radius if it is possible
		if (sanityCheck) {
			auto = auto && canUsePreviousCompAutomatic();
		}

		if (autoForeRadius == auto)
			return;

		autoForeRadius = auto;

		clearPreset();
		fireComponentChangeEvent(ComponentChangeEvent.BOTH_CHANGE);
	}

	public void setForeRadiusAutomatic(boolean auto) {
		setForeRadiusAutomatic(auto, false);
	}

	//////// Aft radius /////////

	@Override
	public double getAftRadius() {
		if (isAftRadiusAutomatic()) {
			aftRadius = getAutoAftRadius();
		}
		return aftRadius;
	}

	/**
	 * Returns the automatic radius from the rear, taken from the next component.
	 * Returns the default radius if there
	 * is no next component.
	 */
	protected double getAutoAftRadius() {
		SymmetricComponent c = this.getNextSymmetricComponent();
		if (c != null) {
			return c.getRearAutoRadius();
		} else {
			return DEFAULT_RADIUS;
		}
	}

	/**
	 * Set the new aft radius, with option to clamp the thickness to the new radius
	 * if it's too large.
	 *
	 * @param radius     new radius
	 * @param doClamping whether to clamp the thickness
	 */
	public void setAftRadius(double radius, boolean doClamping) {
		for (RocketComponent listener : configListeners) {
			if (listener instanceof Transition) {
				((Transition) listener).setAftRadius(radius);
			}
		}

		if ((this.aftRadius == radius) && (autoAftRadius == false))
			return;

		this.autoAftRadius = false;
		this.aftRadius = Math.max(radius, 0);

		if (doClamping && this.thickness > this.foreRadius && this.thickness > this.aftRadius)
			this.thickness = Math.max(this.foreRadius, this.aftRadius);

		clearPreset();
		fireComponentChangeEvent(ComponentChangeEvent.BOTH_CHANGE);

		setAftShoulderRadius(getAftShoulderRadius());
	}

	public void setAftRadius(double radius) {
		setAftRadius(radius, true);
	}

	@Override
	public boolean isAftRadiusAutomatic() {
		return autoAftRadius;
	}

	/**
	 * Set the aft radius to automatic mode (takes its value from the next symmetric
	 * component's radius).
	 *
	 * @param auto        whether to set the aft radius to automatic mode
	 * @param sanityCheck whether to sanity check auto mode for whether there is a
	 *                    next component of which you can take the radius
	 */
	public void setAftRadiusAutomatic(boolean auto, boolean sanityCheck) {
		for (RocketComponent listener : configListeners) {
			if (listener instanceof Transition) {
				((Transition) listener).setAftRadiusAutomatic(auto);
			}
		}

		// You can only set the auto aft radius if it is possible
		if (sanityCheck) {
			auto = auto && canUseNextCompAutomatic();
		}

		if (autoAftRadius == auto)
			return;

		autoAftRadius = auto;

		clearPreset();
		fireComponentChangeEvent(ComponentChangeEvent.BOTH_CHANGE);
	}

	public void setAftRadiusAutomatic(boolean auto) {
		setAftRadiusAutomatic(auto, false);
	}

	//// Radius automatics

	@Override
	protected double getFrontAutoRadius() {
		if (isAftRadiusAutomatic())
			return -1;
		return getAftRadius();
	}

	@Override
	protected double getRearAutoRadius() {
		if (isForeRadiusAutomatic())
			return -1;
		return getForeRadius();
	}

	@Override
	public boolean usesPreviousCompAutomatic() {
		return isForeRadiusAutomatic();
	}

	@Override
	public boolean usesNextCompAutomatic() {
		return isAftRadiusAutomatic();
	}

	/**
	 * Checks whether this component can use the automatic radius of the previous
	 * symmetric component.
	 *
	 * @return false if there is no previous symmetric component, or if the previous
	 *         component already has this component
	 *         as its auto dimension reference
	 */
	public boolean canUsePreviousCompAutomatic() {
		SymmetricComponent referenceComp = getPreviousSymmetricComponent();
		if (referenceComp == null) {
			return false;
		}
		return !referenceComp.usesNextCompAutomatic();
	}

	/**
	 * Checks whether this component can use the automatic radius of the next
	 * symmetric component.
	 *
	 * @return false if there is no next symmetric component, or if the next
	 *         component already has this component
	 *         as its auto dimension reference
	 */
	public boolean canUseNextCompAutomatic() {
		SymmetricComponent referenceComp = getNextSymmetricComponent();
		if (referenceComp == null) {
			return false;
		}
		return !referenceComp.usesPreviousCompAutomatic();
	}

	//////// Type & shape /////////

	public Shape getShapeType() {
		return type;
	}

	public void setShapeType(Shape type) {
		for (RocketComponent listener : configListeners) {
			if (listener instanceof Transition) {
				((Transition) listener).setShapeType(type);
			}
		}

		if (type == null) {
			throw new IllegalArgumentException("setShapeType called with null argument");
		}

		if (this.type == type)
			return;
		this.type = type;
		this.clipped = type.isClippable();
		this.shapeParameter = type.defaultParameter();

		// Need to clearPreset when shape type changes.
		clearPreset();

		fireComponentChangeEvent(ComponentChangeEvent.BOTH_CHANGE);
	}

	public double getShapeParameter() {
		return shapeParameter;
	}

	public void setShapeParameter(double n) {
		for (RocketComponent listener : configListeners) {
			if (listener instanceof Transition) {
				((Transition) listener).setShapeParameter(n);
			}
		}

		if (shapeParameter == n)
			return;
		this.shapeParameter = MathUtil.clamp(n, type.minParameter(), type.maxParameter());

		// Need to clearPreset when shape parameter changes.
		clearPreset();

		fireComponentChangeEvent(ComponentChangeEvent.BOTH_CHANGE);
	}

	public boolean isClipped() {
		if (!type.isClippable())
			return false;
		return clipped;
	}

	public void setClipped(boolean c) {
		for (RocketComponent listener : configListeners) {
			if (listener instanceof Transition) {
				((Transition) listener).setClipped(c);
			}
		}

		if (clipped == c)
			return;
		clipped = c;
		fireComponentChangeEvent(ComponentChangeEvent.BOTH_CHANGE);
	}

	public boolean isClippedEnabled() {
		return type.isClippable();
	}

	public double getShapeParameterMin() {
		return type.minParameter();
	}

	public double getShapeParameterMax() {
		return type.maxParameter();
	}

	//////// Shoulders ////////

	public double getForeShoulderRadius() {
		return foreShoulderRadius;
	}

	public void setForeShoulderRadius(double foreShoulderRadius, boolean doClamping) {
		for (RocketComponent listener : configListeners) {
			if (listener instanceof Transition) {
				((Transition) listener).setForeShoulderRadius(foreShoulderRadius, doClamping);
			}
		}

		if (doClamping) {
			foreShoulderRadius = Math.min(foreShoulderRadius, getForeRadius());
		}

		if (MathUtil.equals(this.foreShoulderRadius, foreShoulderRadius))
			return;

		this.foreShoulderRadius = foreShoulderRadius;

		if (doClamping) {
			this.foreShoulderThickness = Math.min(this.foreShoulderRadius, this.foreShoulderThickness);
		}

		clearPreset();
		fireComponentChangeEvent(ComponentChangeEvent.MASS_CHANGE);
	}

	public void setForeShoulderRadius(double foreShoulderRadius) {
		setForeShoulderRadius(foreShoulderRadius, true);
	}

	public double getForeShoulderThickness() {
		return foreShoulderThickness;
	}

	public void setForeShoulderThickness(double foreShoulderThickness) {
		for (RocketComponent listener : configListeners) {
			if (listener instanceof Transition) {
				((Transition) listener).setForeShoulderThickness(foreShoulderThickness);
			}
		}

		if (MathUtil.equals(this.foreShoulderThickness, foreShoulderThickness))
			return;
		this.foreShoulderThickness = foreShoulderThickness;
		fireComponentChangeEvent(ComponentChangeEvent.MASS_CHANGE);
	}

	public double getForeShoulderLength() {
		return foreShoulderLength;
	}

	public void setForeShoulderLength(double foreShoulderLength) {
		for (RocketComponent listener : configListeners) {
			if (listener instanceof Transition) {
				((Transition) listener).setForeShoulderLength(foreShoulderLength);
			}
		}

		if (MathUtil.equals(this.foreShoulderLength, foreShoulderLength))
			return;
		this.foreShoulderLength = foreShoulderLength;
		fireComponentChangeEvent(ComponentChangeEvent.MASS_CHANGE);
	}

	public boolean isForeShoulderCapped() {
		return foreShoulderCapped;
	}

	public void setForeShoulderCapped(boolean capped) {
		for (RocketComponent listener : configListeners) {
			if (listener instanceof Transition) {
				((Transition) listener).setForeShoulderCapped(capped);
			}
		}

		if (this.foreShoulderCapped == capped)
			return;
		this.foreShoulderCapped = capped;
		fireComponentChangeEvent(ComponentChangeEvent.MASS_CHANGE);
	}

	public double getAftShoulderRadius() {
		return aftShoulderRadius;
	}

	public void setAftShoulderRadius(double aftShoulderRadius, boolean doClamping) {
		for (RocketComponent listener : configListeners) {
			if (listener instanceof Transition) {
				((Transition) listener).setAftShoulderRadius(aftShoulderRadius, doClamping);
			}
		}

		if (doClamping) {
			aftShoulderRadius = Math.min(aftShoulderRadius, getAftRadius());
		}

		if (MathUtil.equals(this.aftShoulderRadius, aftShoulderRadius))
			return;

		this.aftShoulderRadius = aftShoulderRadius;

		if (doClamping) {
			this.aftShoulderThickness = Math.min(this.aftShoulderRadius, this.aftShoulderThickness);
		}

		clearPreset();
		fireComponentChangeEvent(ComponentChangeEvent.MASS_CHANGE);
	}

	public void setAftShoulderRadius(double aftShoulderRadius) {
		setAftShoulderRadius(aftShoulderRadius, true);
	}

	public double getAftShoulderThickness() {
		return aftShoulderThickness;
	}

	public void setAftShoulderThickness(double aftShoulderThickness) {
		for (RocketComponent listener : configListeners) {
			if (listener instanceof Transition) {
				((Transition) listener).setAftShoulderThickness(aftShoulderThickness);
			}
		}

		if (MathUtil.equals(this.aftShoulderThickness, aftShoulderThickness))
			return;
		this.aftShoulderThickness = aftShoulderThickness;
		fireComponentChangeEvent(ComponentChangeEvent.MASS_CHANGE);
	}

	public double getAftShoulderLength() {
		return aftShoulderLength;
	}

	public void setAftShoulderLength(double aftShoulderLength) {
		for (RocketComponent listener : configListeners) {
			if (listener instanceof Transition) {
				((Transition) listener).setAftShoulderLength(aftShoulderLength);
			}
		}

		if (MathUtil.equals(this.aftShoulderLength, aftShoulderLength))
			return;
		this.aftShoulderLength = aftShoulderLength;
		fireComponentChangeEvent(ComponentChangeEvent.MASS_CHANGE);
	}

	public boolean isAftShoulderCapped() {
		return aftShoulderCapped;
	}

	public void setAftShoulderCapped(boolean capped) {
		for (RocketComponent listener : configListeners) {
			if (listener instanceof Transition) {
				((Transition) listener).setAftShoulderCapped(capped);
			}
		}

		if (this.aftShoulderCapped == capped)
			return;
		this.aftShoulderCapped = capped;
		fireComponentChangeEvent(ComponentChangeEvent.MASS_CHANGE);
	}

	/////////// Shape implementations ////////////

	/**
	 * Return the radius at point x of the transition.
	 */
	@Override
	public double getRadius(double x) {
		if (x < 0)
			return getForeRadius();
		if (x >= length)
			return getAftRadius();

		double r1 = getForeRadius();
		double r2 = getAftRadius();

		if (r1 == r2)
			return r1;

		if (r1 > r2) {
			x = length - x;
			double tmp = r1;
			r1 = r2;
			r2 = tmp;
		}

		if (isClipped()) {
			// Check clip calculation
			if (clipLength < 0)
				calculateClip(r1, r2);
			return type.getRadius(clipLength + x, r2, clipLength + length, shapeParameter);
		} else {
			// Not clipped
			return r1 + type.getRadius(x, r2 - r1, length, shapeParameter);
		}
	}

	/**
	 * Numerically solve clipLength from the equation
	 * r1 == type.getRadius(clipLength,r2,clipLength+length)
	 * using a binary search. It assumes getOuterRadius() to be monotonically
	 * increasing.
	 */
	private void calculateClip(double r1, double r2) {
		double min = 0, max = length;

		if (r1 >= r2) {
			double tmp = r1;
			r1 = r2;
			r2 = tmp;
		}

		if (r1 == 0) {
			clipLength = 0;
			return;
		}

		if (length <= 0) {
			clipLength = 0;
			return;
		}

		// Required:
		// getR(min,min+length,r2) - r1 < 0
		// getR(max,max+length,r2) - r1 > 0

		int n = 0;
		while (type.getRadius(max, r2, max + length, shapeParameter) - r1 < 0) {
			min = max;
			max *= 2;
			n++;
			if (n > 10)
				break;
		}

		while (true) {
			clipLength = (min + max) / 2;
			if ((max - min) < CLIP_PRECISION)
				return;
			double val = type.getRadius(clipLength, r2, clipLength + length, shapeParameter);
			if (val - r1 > 0) {
				max = clipLength;
			} else {
				min = clipLength;
			}
		}
	}

	@Override
	public double getInnerRadius(double x) {
		return Math.max(getRadius(x) - thickness, 0);
	}

	@Override
	public Collection<Coordinate> getComponentBounds() {
		Collection<Coordinate> bounds = super.getComponentBounds();
		if (foreShoulderLength > MINFEATURE)
			addBound(bounds, -foreShoulderLength, foreShoulderRadius);
		if (aftShoulderLength > MINFEATURE)
			addBound(bounds, getLength() + aftShoulderLength, aftShoulderRadius);
		return bounds;
	}

	@Override
	protected void calculateProperties() {
		super.calculateProperties();

		// only adjust properties if there is in fact at least one shoulder
		if ((getForeShoulderLength() > MINFEATURE) || (getAftShoulderLength() > MINFEATURE)) {
			// we'll work with volumes and not masses because density is uniform and it'll
			// save some multiplications and divisions

			// accumulate data for later calculation of properties with shoulders added
			double transVolume = volume;
			double transLongMOI = longitudinalUnitInertia * transVolume;
			double transRotMOI = rotationalUnitInertia * transVolume;
			final Coordinate transCG = cg;

			double foreCapVolume = 0.0;
			Coordinate foreCapCG = Coordinate.ZERO;
			double foreCapLongMOI = 0.0;
			double foreCapRotMOI = 0.0;
			if (isForeShoulderCapped()) {
				final double ir = Math.max(getForeShoulderRadius() - getForeShoulderThickness(), 0);

				foreCapCG = ringCG(ir, 0, -getForeShoulderLength(),
						getForeShoulderThickness() - getForeShoulderLength(),
						getMaterial().getDensity());

				foreCapVolume = ringVolume(ir, 0, getForeShoulderThickness());

				foreCapLongMOI = ringLongitudinalUnitInertia(ir, 0, getForeShoulderThickness()) * foreCapVolume;

				foreCapRotMOI += ringRotationalUnitInertia(ir, 0.0) * foreCapVolume;
			}

			double foreShoulderVolume = 0.0;
			Coordinate foreShoulderCG = Coordinate.ZERO;
			double foreShoulderLongMOI = 0.0;
			double foreShoulderRotMOI = 0.0;
			if (getForeShoulderLength() > MINFEATURE) {
				final double or = getForeShoulderRadius();
				final double ir = Math.max(getForeShoulderRadius() - getForeShoulderThickness(), 0);

				foreShoulderCG = ringCG(getForeShoulderRadius(), ir, -getForeShoulderLength(), 0,
						getMaterial().getDensity());

				foreShoulderVolume = ringVolume(or, ir, getForeShoulderLength());

				foreShoulderLongMOI = ringLongitudinalUnitInertia(or, ir, getForeShoulderLength()) * foreShoulderVolume;

				foreShoulderRotMOI = ringRotationalUnitInertia(or, ir) * foreShoulderVolume;
			}

			double aftShoulderVolume = 0.0;
			Coordinate aftShoulderCG = Coordinate.ZERO;
			double aftShoulderLongMOI = 0.0;
			double aftShoulderRotMOI = 0.0;
			if (getAftShoulderLength() > MINFEATURE) {
				final double or = getAftShoulderRadius();
				final double ir = Math.max(getAftShoulderRadius() - getAftShoulderThickness(), 0);

				aftShoulderCG = ringCG(getAftShoulderRadius(), ir, getLength(),
									   getLength() + getAftShoulderLength(),
									   getMaterial().getDensity());

				aftShoulderVolume = ringVolume(or, ir, getAftShoulderLength());

				aftShoulderLongMOI = ringLongitudinalUnitInertia(or, ir, getAftShoulderLength())*aftShoulderVolume;

				aftShoulderRotMOI = ringRotationalUnitInertia(or, ir) * aftShoulderVolume;
			}

			double aftCapVolume = 0.0;
			Coordinate aftCapCG = Coordinate.ZERO;
			double aftCapLongMOI = 0.0;
			double aftCapRotMOI = 0.0;
			if (isAftShoulderCapped()) {
				final double ir = Math.max(getAftShoulderRadius() - getAftShoulderThickness(), 0);

				aftCapCG = ringCG(ir, 0,
								  getLength() + getAftShoulderLength() - getAftShoulderThickness(),
								  getLength() + getAftShoulderLength(), getMaterial().getDensity());

				aftCapVolume = ringVolume(ir, 0, getAftShoulderThickness() );

				aftCapLongMOI = ringLongitudinalUnitInertia(ir, 0, getForeShoulderThickness())*aftCapVolume;

				aftCapRotMOI = ringRotationalUnitInertia(ir, 0.0) * aftCapVolume;
			}

			// Combine results
			volume = foreCapVolume + foreShoulderVolume + transVolume + aftShoulderVolume + aftCapVolume;

			final double cgx = foreCapCG.x * foreCapCG.weight +
				foreShoulderCG.x * foreShoulderCG.weight +
				transCG.x * transCG.weight +
				aftShoulderCG.x * aftShoulderCG.weight +
				aftCapCG.x * aftCapCG.weight;

			final double mass = foreCapCG.weight + foreShoulderCG.weight + transCG.weight + aftShoulderCG.weight + aftCapCG.weight;
			
			// If the mass is 0, so are moments of inertia
			if (mass < MathUtil.EPSILON) {
				cg = new Coordinate(0, 0, 0, 0);
				longitudinalUnitInertia = 0.0;
				rotationalUnitInertia = 0.0;

				return;
			}
			
			cg = new Coordinate(cgx / mass, 0, 0, mass);

			// need to use parallel axis theorem to move longitudinal MOI to CG of component
			foreCapLongMOI += pow2(cg.x - foreCapCG.x) * foreCapVolume;
			foreShoulderLongMOI += pow2(cg.x - foreShoulderCG.x) * foreShoulderVolume;
			transLongMOI += pow2(cg.x - transCG.x) * transVolume;
			aftShoulderLongMOI += pow2(cg.x - aftShoulderCG.x) * aftShoulderVolume;
			aftCapLongMOI += pow2(cg.x - aftCapCG.x) * aftCapVolume;

			final double longMOI = foreCapLongMOI + foreShoulderLongMOI + transLongMOI + aftShoulderLongMOI + aftCapLongMOI;
			longitudinalUnitInertia = longMOI/volume;

			final double rotMOI = foreCapRotMOI + foreShoulderRotMOI + transRotMOI + aftShoulderRotMOI + aftCapRotMOI;
			rotationalUnitInertia = rotMOI/volume;
		}
 	}

	/**
	 * Returns the name of the component ("Transition").
	 */
	@Override
	public String getComponentName() {
		//// Transition
		return trans.get("Transition.Transition");
	}

	@Override
	protected void componentChanged(ComponentChangeEvent e) {
		super.componentChanged(e);
		clipLength = -1;
	}

	/**
	 * Check whether the given type can be added to this component. Transitions
	 * allow any
	 * InternalComponents to be added.
	 *
	 * @param comptype The RocketComponent class type to add.
	 * @return Whether such a component can be added.
	 */
	@Override
	public boolean isCompatible(Class<? extends RocketComponent> comptype) {
		if (InternalComponent.class.isAssignableFrom(comptype)) {
			return true;
		} else if (FreeformFinSet.class.isAssignableFrom(comptype)) {
			return true;
		}
		return false;
	}

	@Override
	public Type getPresetType() {
		return ComponentPreset.Type.TRANSITION;
	}

	@Override
	protected void loadFromPreset(ComponentPreset preset) {

		boolean presetFilled = false;
		if (preset.has(ComponentPreset.FILLED)) {
			presetFilled = preset.get(ComponentPreset.FILLED);
		}

		if (preset.has(ComponentPreset.SHAPE)) {
			Shape s = preset.get(ComponentPreset.SHAPE);
			this.setShapeType(s);
			this.setClipped(s.canClip);
			this.setShapeParameter(s.defaultParameter());
		}
		if (preset.has(ComponentPreset.AFT_OUTER_DIAMETER)) {
			double outerDiameter = preset.get(ComponentPreset.AFT_OUTER_DIAMETER);
			this.setAftRadiusAutomatic(false);
			this.setAftRadius(outerDiameter / 2.0);
		}
		if (preset.has(ComponentPreset.AFT_SHOULDER_LENGTH)) {
			double d = preset.get(ComponentPreset.AFT_SHOULDER_LENGTH);
			this.setAftShoulderLength(d);
		}
		if (preset.has(ComponentPreset.AFT_SHOULDER_DIAMETER)) {
			double d = preset.get(ComponentPreset.AFT_SHOULDER_DIAMETER);
			this.setAftShoulderRadius(d / 2.0);
			if (presetFilled) {
				this.setAftShoulderThickness(d / 2.0);
			}
		}
		if (preset.has(ComponentPreset.FORE_OUTER_DIAMETER)) {
			double outerDiameter = preset.get(ComponentPreset.FORE_OUTER_DIAMETER);
			this.setForeRadiusAutomatic(false);
			this.setForeRadius(outerDiameter / 2.0);
		}
		if (preset.has(ComponentPreset.FORE_SHOULDER_LENGTH)) {
			double d = preset.get(ComponentPreset.FORE_SHOULDER_LENGTH);
			this.setForeShoulderLength(d);
		}
		if (preset.has(ComponentPreset.FORE_SHOULDER_DIAMETER)) {
			double d = preset.get(ComponentPreset.FORE_SHOULDER_DIAMETER);
			this.setForeShoulderRadius(d / 2.0);
			if (presetFilled) {
				this.setForeShoulderThickness(d / 2.0);
			}
		}

		super.loadFromPreset(preset);

		fireComponentChangeEvent(ComponentChangeEvent.BOTH_CHANGE);

	}

	@Override
	public InsideColorComponentHandler getInsideColorComponentHandler() {
		return this.insideColorComponentHandler;
	}

	@Override
	public void setInsideColorComponentHandler(InsideColorComponentHandler handler) {
		this.insideColorComponentHandler = handler;
	}

	/**
	 * An enumeration listing the possible shapes of transitions.
	 *
	 * @author Sampo Niskanen <sampo.niskanen@iki.fi>
	 */
	public static enum Shape {

		/**
		 * Conical shape.
		 */
		//// Conical
		CONICAL(trans.get("Shape.Conical"),
				//// A conical nose cone has a profile of a triangle.
				trans.get("Shape.Conical.desc1"),
				//// A conical transition has straight sides.
				trans.get("Shape.Conical.desc2")) {
			@Override
			public double getRadius(double x, double radius, double length, double param) {
				assert x >= 0;
				assert x <= length;
				assert radius >= 0;
				return radius * x / length;
			}
		},

		/**
		 * Ogive shape. The shape parameter is the portion of an extended tangent ogive
		 * that will be used. That is, for param==1 a tangent ogive will be produced,
		 * and
		 * for smaller values the shape straightens out into a cone at param==0.
		 */
		//// Ogive
		OGIVE(trans.get("Shape.Ogive"),
				//// An ogive nose cone has a profile that is a segment of a circle. The shape
				//// parameter value 1 produces a <b>tangent ogive</b>, which has a smooth
				//// transition to the body tube, values less than 1 produce <b>secant
				//// ogives</b>.
				trans.get("Shape.Ogive.desc1"),
				//// An ogive transition has a profile that is a segment of a circle. The shape
				//// parameter value 1 produces a <b>tangent ogive</b>, which has a smooth
				//// transition to the body tube at the aft end, values less than 1 produce
				//// <b>secant ogives</b>.
				trans.get("Shape.Ogive.desc2")) {
			@Override
			public boolean usesParameter() {
				return true; // Range 0...1 is default
			}

			@Override
			public double defaultParameter() {
				return 1.0; // Tangent ogive by default
			}

			@Override
			public double getRadius(double x, double radius, double length, double param) {
				assert x >= 0;
				assert x <= length;
				assert radius >= 0;
				assert param >= 0;
				assert param <= 1;

				// Impossible to calculate ogive for length < radius, scale instead
				// TODO: LOW: secant ogive could be calculated lower
				if (length < radius) {
					x = x * radius / length;
					length = radius;
				}

				if (param < MINFEATURE)
					return CONICAL.getRadius(x, radius, length, param);

				// Radius of circle is:
				double R = MathUtil.safeSqrt((pow2(length) + pow2(radius)) *
						(pow2((2 - param) * length) + pow2(param * radius)) / (4 * pow2(param * radius)));
				double L = length / param;
				// double R = (radius + length*length/(radius*param*param))/2;
				double y0 = MathUtil.safeSqrt(R * R - L * L);
				return MathUtil.safeSqrt(R * R - (L - x) * (L - x)) - y0;
			}
		},

		/**
		 * Ellipsoidal shape.
		 */
		//// Ellipsoid
		ELLIPSOID(trans.get("Shape.Ellipsoid"),
				//// An ellipsoidal nose cone has a profile of a half-ellipse with major axes of
				//// lengths 2&times;<i>Length</i> and <i>Diameter</i>.
				trans.get("Shape.Ellipsoid.desc1"),
				//// An ellipsoidal transition has a profile of a half-ellipse with major axes
				//// of lengths 2&times;<i>Length</i> and <i>Diameter</i>. If the transition is
				//// not clipped, then the profile is extended at the center by the
				//// corresponding radius.
				trans.get("Shape.Ellipsoid.desc2"), true) {
			@Override
			public double getRadius(double x, double radius, double length, double param) {
				assert x >= 0;
				assert x <= length;
				assert radius >= 0;
				x = x * radius / length;
				return MathUtil.safeSqrt(2 * radius * x - x * x); // radius/length * sphere
			}
		},

		//// Power series
		POWER(trans.get("Shape.Powerseries"),
				trans.get("Shape.Powerseries.desc1"),
				trans.get("Shape.Powerseries.desc2"), true) {
			@Override
			public boolean usesParameter() { // Range 0...1
				return true;
			}

			@Override
			public double defaultParameter() {
				return 0.5;
			}

			@Override
			public double getRadius(double x, double radius, double length, double param) {
				assert x >= 0;
				assert x <= length;
				assert radius >= 0;
				assert param >= 0;
				assert param <= 1;
				if (param <= 0.00001) {
					if (x <= 0.00001)
						return 0;
					else
						return radius;
				}
				return radius * Math.pow(x / length, param);
			}

		},

		//// Parabolic series
		PARABOLIC(trans.get("Shape.Parabolicseries"),
				//// A parabolic series nose cone has a profile of a parabola. The shape
				//// parameter defines the segment of the parabola to utilize. The shape
				//// parameter 1.0 produces a <b>full parabola</b> which is tangent to the body
				//// tube, 0.75 produces a <b>3/4 parabola</b>, 0.5 procudes a <b>1/2
				//// parabola</b> and 0 produces a <b>conical</b> nose cone.
				trans.get("Shape.Parabolicseries.desc1"),
				//// A parabolic series transition has a profile of a parabola. The shape
				//// parameter defines the segment of the parabola to utilize. The shape
				//// parameter 1.0 produces a <b>full parabola</b> which is tangent to the body
				//// tube at the aft end, 0.75 produces a <b>3/4 parabola</b>, 0.5 procudes a
				//// <b>1/2 parabola</b> and 0 produces a <b>conical</b> transition.
				trans.get("Shape.Parabolicseries.desc2")) {

			// In principle a parabolic transition is clippable, but the difference is
			// negligible.

			@Override
			public boolean usesParameter() { // Range 0...1
				return true;
			}

			@Override
			public double defaultParameter() {
				return 1.0;
			}

			@Override
			public double getRadius(double x, double radius, double length, double param) {
				assert x >= 0;
				assert x <= length;
				assert radius >= 0;
				assert param >= 0;
				assert param <= 1;

				return radius * ((2 * x / length - param * pow2(x / length)) / (2 - param));
			}
		},

		//// Haack series
		HAACK(trans.get("Shape.Haackseries"),
				//// The Haack series nose cones are designed to minimize drag. The shape
				//// parameter 0 produces an <b>LD-Haack</b> or <b>Von Karman</b> nose cone,
				//// which minimizes drag for fixed length and diameter, while a value of 0.333
				//// produces an <b>LV-Haack</b> nose cone, which minimizes drag for fixed
				//// length and volume.
				trans.get("Shape.Haackseries.desc1"),
				//// The Haack series <i>nose cones</i> are designed to minimize drag. These
				//// transition shapes are their equivalents, but do not necessarily produce
				//// optimal drag for transitions. The shape parameter 0 produces an
				//// <b>LD-Haack</b> or <b>Von Karman</b> shape, while a value of 0.333 produces
				//// an <b>LV-Haack</b> shape.
				trans.get("Shape.Haackseries.desc2"), true) {

			@Override
			public boolean usesParameter() {
				return true;
			}

			@Override
			public double maxParameter() {
				return 1.0 / 3.0; // Range 0...1/3
			}

			@Override
			public double getRadius(double x, double radius, double length, double param) {
				assert x >= 0;
				assert x <= length;
				assert radius >= 0;
				assert param >= 0;
				assert param <= 2;

				double theta = Math.acos(1 - 2 * x / length);
				if (MathUtil.equals(param, 0)) {
					return radius * MathUtil.safeSqrt((theta - sin(2 * theta) / 2) / Math.PI);
				}
				return radius * MathUtil.safeSqrt((theta - sin(2 * theta) / 2 + param * pow3(sin(theta))) / Math.PI);
			}
		},

		// POLYNOMIAL("Smooth polynomial",
		// "A polynomial is fitted such that the nose cone profile is horizontal "+
		// "at the aft end of the transition. The angle at the tip is defined by "+
		// "the shape parameter.",
		// "A polynomial is fitted such that the transition profile is horizontal "+
		// "at the aft end of the transition. The angle at the fore end is defined "+
		// "by the shape parameter.") {
		// @Override
		// public boolean usesParameter() {
		// return true;
		// }
		// @Override
		// public double maxParameter() {
		// return 3.0; // Range 0...3
		// }
		// @Override
		// public double defaultParameter() {
		// return 0.0;
		// }
		// public double getRadius(double x, double radius, double length, double param)
		// {
		// assert x >= 0;
		// assert x <= length;
		// assert radius >= 0;
		// assert param >= 0;
		// assert param <= 3;
		// // p(x) = (k-2)x^3 + (3-2k)x^2 + k*x
		// x = x/length;
		// return radius*((((param-2)*x + (3-2*param))*x + param)*x);
		// }
		// }
		;

		// Privete fields of the shapes
		private final String name;
		private final String transitionDesc;
		private final String noseconeDesc;
		private final boolean canClip;

		// Non-clippable constructor
		Shape(String name, String noseconeDesc, String transitionDesc) {
			this(name, noseconeDesc, transitionDesc, false);
		}

		// Clippable constructor
		Shape(String name, String noseconeDesc, String transitionDesc, boolean canClip) {
			this.name = name;
			this.canClip = canClip;
			this.noseconeDesc = noseconeDesc;
			this.transitionDesc = transitionDesc;
		}

		/**
		 * Return the name of the transition shape name.
		 */
		public String getName() {
			return name;
		}

		/**
		 * Get a description of the Transition shape.
		 */
		public String getTransitionDescription() {
			return transitionDesc;
		}

		/**
		 * Get a description of the NoseCone shape.
		 */
		public String getNoseConeDescription() {
			return noseconeDesc;
		}

		/**
		 * Check whether the shape differs in clipped mode. The clipping should be
		 * enabled by default if possible.
		 */
		public boolean isClippable() {
			return canClip;
		}

		/**
		 * Return whether the shape uses the shape parameter. (Default false.)
		 */
		public boolean usesParameter() {
			return false;
		}

		/**
		 * Return the minimum value of the shape parameter. (Default 0.)
		 */
		public double minParameter() {
			return 0.0;
		}

		/**
		 * Return the maximum value of the shape parameter. (Default 1.)
		 */
		public double maxParameter() {
			return 1.0;
		}

		/**
		 * Return the default value of the shape parameter. (Default 0.)
		 */
		public double defaultParameter() {
			return 0.0;
		}

		/**
		 * Calculate the basic radius of a transition with the given radius, length and
		 * shape parameter at the point x from the tip of the component. It is assumed
		 * that the fore radius if zero and the aft radius is <code>radius >= 0</code>.
		 * Boattails are achieved by reversing the component.
		 *
		 * @param x      Position from the tip of the component.
		 * @param radius Aft end radius >= 0.
		 * @param length Length of the transition >= 0.
		 * @param param  Valid shape parameter.
		 * @return The basic radius at the given position.
		 */
		public abstract double getRadius(double x, double radius, double length, double param);

		/**
		 * Returns the name of the shape (same as getName()).
		 */
		@Override
		public String toString() {
			return name;
		}

		/**
		 * Lookup the Shape given the localized name. This differs from the standard
		 * valueOf as that looks up
		 * based on the canonical name, not the localized name which is an instance var.
		 *
		 * @param localizedName
		 * @return
		 */
		public static Shape toShape(String localizedName) {
			Shape[] values = Shape.values();
			for (Shape value : values) {
				if (value.getName().equals(localizedName)) {
					return value;
				}
			}
			return null;
		}
	}

}
