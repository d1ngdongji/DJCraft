package otto.djgun.djcraft.loader;

public final class TrackPackIdValidator {
    public static final int MAX_LENGTH = 128;

    private TrackPackIdValidator() {
    }

    public static boolean isValid(String id) {
        if (id == null || id.isEmpty() || id.length() > MAX_LENGTH || id.equals(".") || id.equals("..")) {
            return false;
        }
        for (int i = 0; i < id.length(); i++) {
            char c = id.charAt(i);
            if ((c < 'a' || c > 'z') && (c < '0' || c > '9')
                    && c != '_' && c != '-' && c != '.') {
                return false;
            }
        }
        return true;
    }
}
