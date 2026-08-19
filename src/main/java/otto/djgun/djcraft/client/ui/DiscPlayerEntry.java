package otto.djgun.djcraft.client.ui;

import otto.djgun.djcraft.data.DiscPlaybackReference;
import otto.djgun.djcraft.data.DiscStatistics;

record DiscPlayerEntry(DiscPlaybackReference reference, DiscStatistics statistics, long snapshotPlaybackMs) {
}
