package macro.builder.analysis;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class ShootoutRun {
    public final ShootoutContext context;
    public final List<ShootoutResult> results;

    public ShootoutRun(ShootoutContext context, List<ShootoutResult> results) {
        this.context = context;
        this.results = Collections.unmodifiableList(new ArrayList<ShootoutResult>(
                results == null ? Collections.<ShootoutResult>emptyList() : results));
    }
}
