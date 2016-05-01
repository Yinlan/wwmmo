package au.com.codeka.warworlds.server.world;

import org.mapdb.Atomic;
import org.mapdb.HTreeMap;

import java.util.HashMap;
import java.util.Map;

import au.com.codeka.warworlds.common.proto.Empire;
import au.com.codeka.warworlds.server.store.DataStore;

/**
 * Manages empires, keeps them loaded and ensure they get saved to the data store at the right time.
 */
public class EmpireManager {
  public static final EmpireManager i = new EmpireManager();

  private Atomic.Long idGenerator;
  private HTreeMap<Long, Empire> empires;
  private final Map<Long, WatchableObject<Empire>> watchedEmpires;

  private EmpireManager() {
    idGenerator = DataStore.i.idGenerator("EmpireID");
    empires = DataStore.i.empires();
    watchedEmpires = new HashMap<>();
  }

  public WatchableObject<Empire> getEmpire(long id) {
    synchronized (watchedEmpires) {
      WatchableObject<Empire> watchableEmpire = watchedEmpires.get(id);
      if (watchableEmpire == null) {
        watchableEmpire = watchEmpire(empires.get(id));
      }
      return watchableEmpire;
    }
  }

  public WatchableObject<Empire> createEmpire(String name) {
    long id = idGenerator.incrementAndGet();
    Empire empire = new Empire.Builder()
        .display_name(name)
        .id(id)
        .build();
    empires.put(id, empire);

    return watchEmpire(empire);
  }

  private WatchableObject<Empire> watchEmpire(Empire empire) {
    WatchableObject<Empire> watchableEmpire;
    synchronized (watchedEmpires) {
      watchableEmpire = watchedEmpires.get(empire.id);
      if (watchableEmpire != null) {
        watchableEmpire.set(empire);
      } else {
        watchableEmpire = new WatchableObject<>(empire.id, empire);
        watchedEmpires.put(watchableEmpire.get().id, watchableEmpire);
      }
    }
    return watchableEmpire;
  }
}