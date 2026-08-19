package otto.djgun.djcraft.cybergrind;

import java.util.List;

import otto.djgun.djcraft.data.DiscPlaybackReference;

final class CyberGrindPlaylistRules {
    private CyberGrindPlaylistRules() {
    }

    static int indexForDiscSlot(List<DiscPlaybackReference> playlist, int discSlot) {
        for (int index = 0; index < playlist.size(); index++) {
            if (playlist.get(index).discSlot() == discSlot) {
                return index;
            }
        }
        return -1;
    }
}
