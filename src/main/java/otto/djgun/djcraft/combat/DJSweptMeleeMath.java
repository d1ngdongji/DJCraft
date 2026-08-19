package otto.djgun.djcraft.combat;

import java.util.ArrayList;
import java.util.List;

/** Pure support-mapped collision math for continuously swept melee volumes. */
public final class DJSweptMeleeMath {
    private static final double EPSILON = 1.0E-8;
    private static final int GJK_ITERATIONS = 32;

    private DJSweptMeleeMath() {
    }

    public static Volume softCone(Vector start, Vector end, Vector look,
            double reach, double horizontalAngleDegrees, double verticalAngleDegrees) {
        Basis basis = Basis.fromLook(look);
        double halfWidth = reach * Math.tan(Math.toRadians(horizontalAngleDegrees));
        double halfHeight = reach * Math.tan(Math.toRadians(verticalAngleDegrees));
        return volume(start, end, basis.forward.scale(reach * 0.5), direction -> {
            Vector far = basis.forward.scale(reach)
                    .add(basis.right.scale(signedExtent(direction.dot(basis.right), halfWidth)))
                    .add(basis.up.scale(signedExtent(direction.dot(basis.up), halfHeight)));
            return direction.dot(far) > 0.0 ? far : Vector.ZERO;
        });
    }

    public static Volume directRay(Vector start, Vector end, Vector look, double reach) {
        return softCone(start, end, look, reach, 0.0, 0.0);
    }

    public static Volume oneEndedCapsule(Vector start, Vector end, Vector look,
            double cylinderLength, double radius) {
        Basis basis = Basis.fromLook(look);
        return volume(start, end, basis.forward.scale(cylinderLength * 0.5), direction -> {
            double axial = direction.dot(basis.forward);
            if (axial >= 0.0) {
                return basis.forward.scale(cylinderLength)
                        .add(direction.normalizeOr(Vector.ZERO).scale(radius));
            }
            Vector radial = direction.subtract(basis.forward.scale(axial));
            return radial.normalizeOr(Vector.ZERO).scale(radius);
        });
    }

    private static Volume volume(Vector start, Vector end, Vector localCenter, Support localSupport) {
        Support support = direction -> {
            Vector translation = direction.dot(end) > direction.dot(start) ? end : start;
            return translation.add(localSupport.support(direction));
        };
        return new Volume(support, start.add(end).scale(0.5).add(localCenter));
    }

    private static double signedExtent(double projection, double extent) {
        return projection < 0.0 ? -extent : extent;
    }

    public record Vector(double x, double y, double z) {
        public static final Vector ZERO = new Vector(0.0, 0.0, 0.0);

        public Vector add(Vector other) {
            return new Vector(x + other.x, y + other.y, z + other.z);
        }

        public Vector subtract(Vector other) {
            return new Vector(x - other.x, y - other.y, z - other.z);
        }

        public Vector scale(double amount) {
            return new Vector(x * amount, y * amount, z * amount);
        }

        public Vector negate() {
            return scale(-1.0);
        }

        public double dot(Vector other) {
            return x * other.x + y * other.y + z * other.z;
        }

        public Vector cross(Vector other) {
            return new Vector(y * other.z - z * other.y, z * other.x - x * other.z,
                    x * other.y - y * other.x);
        }

        public double lengthSquared() {
            return dot(this);
        }

        public Vector normalizeOr(Vector fallback) {
            double lengthSquared = lengthSquared();
            return lengthSquared < EPSILON ? fallback : scale(1.0 / Math.sqrt(lengthSquared));
        }
    }

    public record Box(double minX, double minY, double minZ,
            double maxX, double maxY, double maxZ) {
        public Vector center() {
            return new Vector((minX + maxX) * 0.5, (minY + maxY) * 0.5, (minZ + maxZ) * 0.5);
        }

        Vector support(Vector direction) {
            return new Vector(direction.x >= 0.0 ? maxX : minX,
                    direction.y >= 0.0 ? maxY : minY,
                    direction.z >= 0.0 ? maxZ : minZ);
        }
    }

    public static final class Volume {
        private final Support support;
        private final Vector center;
        private final Box bounds;

        private Volume(Support support, Vector center) {
            this.support = support;
            this.center = center;
            Vector px = support.support(new Vector(1.0, 0.0, 0.0));
            Vector nx = support.support(new Vector(-1.0, 0.0, 0.0));
            Vector py = support.support(new Vector(0.0, 1.0, 0.0));
            Vector ny = support.support(new Vector(0.0, -1.0, 0.0));
            Vector pz = support.support(new Vector(0.0, 0.0, 1.0));
            Vector nz = support.support(new Vector(0.0, 0.0, -1.0));
            this.bounds = new Box(nx.x, ny.y, nz.z, px.x, py.y, pz.z);
        }

        public Box bounds() {
            return bounds;
        }

        public boolean intersects(Box box) {
            Vector direction = box.center().subtract(center);
            if (direction.lengthSquared() < EPSILON) {
                direction = new Vector(1.0, 0.0, 0.0);
            }
            List<Vector> simplex = new ArrayList<>(4);
            Vector point = minkowskiSupport(box, direction);
            simplex.add(point);
            direction = point.negate();
            for (int iteration = 0; iteration < GJK_ITERATIONS; iteration++) {
                if (direction.lengthSquared() < EPSILON) {
                    return true;
                }
                point = minkowskiSupport(box, direction);
                if (point.dot(direction) < -EPSILON) {
                    return false;
                }
                simplex.add(0, point);
                Direction next = new Direction(direction);
                if (updateSimplex(simplex, next)) {
                    return true;
                }
                direction = next.value;
            }
            return false;
        }

        private Vector minkowskiSupport(Box box, Vector direction) {
            return support.support(direction).subtract(box.support(direction.negate()));
        }
    }

    private static boolean updateSimplex(List<Vector> simplex, Direction direction) {
        return switch (simplex.size()) {
            case 2 -> line(simplex, direction);
            case 3 -> triangle(simplex, direction);
            case 4 -> tetrahedron(simplex, direction);
            default -> false;
        };
    }

    private static boolean line(List<Vector> simplex, Direction direction) {
        Vector a = simplex.get(0);
        Vector b = simplex.get(1);
        Vector ab = b.subtract(a);
        Vector ao = a.negate();
        if (sameDirection(ab, ao)) {
            direction.value = tripleCross(ab, ao, ab);
        } else {
            simplex.remove(1);
            direction.value = ao;
        }
        return direction.value.lengthSquared() < EPSILON;
    }

    private static boolean triangle(List<Vector> simplex, Direction direction) {
        Vector a = simplex.get(0);
        Vector b = simplex.get(1);
        Vector c = simplex.get(2);
        Vector ab = b.subtract(a);
        Vector ac = c.subtract(a);
        Vector ao = a.negate();
        Vector abc = ab.cross(ac);

        if (sameDirection(abc.cross(ac), ao)) {
            if (sameDirection(ac, ao)) {
                simplex.remove(1);
                direction.value = tripleCross(ac, ao, ac);
            } else {
                return reduceToLine(simplex, direction, ab, ao);
            }
        } else if (sameDirection(ab.cross(abc), ao)) {
            return reduceToLine(simplex, direction, ab, ao);
        } else if (sameDirection(abc, ao)) {
            direction.value = abc;
        } else {
            simplex.set(1, c);
            simplex.set(2, b);
            direction.value = abc.negate();
        }
        return direction.value.lengthSquared() < EPSILON;
    }

    private static boolean reduceToLine(List<Vector> simplex, Direction direction, Vector ab, Vector ao) {
        simplex.remove(2);
        if (sameDirection(ab, ao)) {
            direction.value = tripleCross(ab, ao, ab);
        } else {
            simplex.remove(1);
            direction.value = ao;
        }
        return direction.value.lengthSquared() < EPSILON;
    }

    private static boolean tetrahedron(List<Vector> simplex, Direction direction) {
        Vector a = simplex.get(0);
        Vector b = simplex.get(1);
        Vector c = simplex.get(2);
        Vector d = simplex.get(3);
        Vector ao = a.negate();
        Face[] faces = {
                Face.outward(a, b, c, d, 1, 2),
                Face.outward(a, c, d, b, 2, 3),
                Face.outward(a, d, b, c, 3, 1)
        };
        for (Face face : faces) {
            if (sameDirection(face.normal, ao)) {
                Vector second = simplex.get(face.secondIndex);
                Vector third = simplex.get(face.thirdIndex);
                simplex.clear();
                simplex.add(a);
                simplex.add(second);
                simplex.add(third);
                direction.value = face.normal;
                return false;
            }
        }
        return true;
    }

    private static Vector tripleCross(Vector a, Vector b, Vector c) {
        Vector result = a.cross(b).cross(c);
        if (result.lengthSquared() >= EPSILON) {
            return result;
        }
        Vector fallback = Math.abs(a.x) < 0.9 ? new Vector(1.0, 0.0, 0.0) : new Vector(0.0, 1.0, 0.0);
        return a.cross(fallback).cross(a);
    }

    private static boolean sameDirection(Vector direction, Vector toward) {
        return direction.dot(toward) > EPSILON;
    }

    @FunctionalInterface
    private interface Support {
        Vector support(Vector direction);
    }

    private static final class Direction {
        private Vector value;

        private Direction(Vector value) {
            this.value = value;
        }
    }

    private record Basis(Vector forward, Vector right, Vector up) {
        private static Basis fromLook(Vector look) {
            Vector forward = look.normalizeOr(new Vector(0.0, 0.0, 1.0));
            Vector worldUp = new Vector(0.0, 1.0, 0.0);
            Vector right = forward.cross(worldUp).normalizeOr(new Vector(1.0, 0.0, 0.0));
            Vector up = right.cross(forward).normalizeOr(worldUp);
            return new Basis(forward, right, up);
        }
    }

    private record Face(Vector normal, int secondIndex, int thirdIndex) {
        private static Face outward(Vector a, Vector b, Vector c, Vector opposite,
                int secondIndex, int thirdIndex) {
            Vector normal = b.subtract(a).cross(c.subtract(a));
            if (normal.dot(opposite.subtract(a)) > 0.0) {
                normal = normal.negate();
                int swap = secondIndex;
                secondIndex = thirdIndex;
                thirdIndex = swap;
            }
            return new Face(normal, secondIndex, thirdIndex);
        }
    }
}
