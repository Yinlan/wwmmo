package au.com.codeka.warworlds.server.events;

import java.util.ArrayList;

import org.joda.time.DateTime;

import javax.annotation.Nullable;

import au.com.codeka.common.Log;
import au.com.codeka.common.model.BaseColony;
import au.com.codeka.common.model.DesignKind;
import au.com.codeka.common.model.Simulation;
import au.com.codeka.common.protobuf.Messages;
import au.com.codeka.warworlds.server.Configuration;
import au.com.codeka.warworlds.server.Event;
import au.com.codeka.warworlds.server.RequestContext;
import au.com.codeka.warworlds.server.RequestException;
import au.com.codeka.warworlds.server.ctrl.BaseDataBase;
import au.com.codeka.warworlds.server.ctrl.BuildingController;
import au.com.codeka.warworlds.server.ctrl.EmpireController;
import au.com.codeka.warworlds.server.ctrl.FleetController;
import au.com.codeka.warworlds.server.ctrl.SituationReportController;
import au.com.codeka.warworlds.server.ctrl.StarController;
import au.com.codeka.warworlds.server.data.DB;
import au.com.codeka.warworlds.server.data.SqlResult;
import au.com.codeka.warworlds.server.data.SqlStmt;
import au.com.codeka.warworlds.server.data.Transaction;
import au.com.codeka.warworlds.server.model.Colony;
import au.com.codeka.warworlds.server.model.Empire;
import au.com.codeka.warworlds.server.model.Fleet;
import au.com.codeka.warworlds.server.model.Star;

public class BuildCompleteEvent extends Event {
  private final Log log = new Log("BuildCompleteEvent");
  private DataBase db;

  public BuildCompleteEvent() {
    db = new DataBase();
  }

  public BuildCompleteEvent(Transaction t) {
    db = new DataBase(t);
  }

  @Override
  public String getNextEventTimeSql() {
    return "SELECT MIN(end_time) FROM build_requests WHERE processing = 0";
  }

  @Override
  public void process() {
    ArrayList<Integer> processedIDs = new ArrayList<>();
    // Just do ten at a time, which will allow us to interleave other events.
    String sql = "SELECT" +
        " id, star_id, colony_id, empire_id, existing_building_id, existing_fleet_id, upgrade_id," +
        " design_kind, design_id, count, notes, disable_notification " +
        "FROM build_requests " +
        "WHERE end_time < ?" +
        " AND processing = 0" +
        "LIMIT 10";
    try (SqlStmt stmt = DB.prepare(sql)) {
      // Anything in the next 10 seconds is a candidate.
      stmt.setDateTime(1, DateTime.now().plusSeconds(10));
      SqlResult res = stmt.select();
      while (res.next()) {
        int id = res.getInt(1);

        RequestContext.i.setContext("event: BuildCompleteEvent build_request.id=" + id);

        sql = "UPDATE build_requests SET processing = 1 WHERE processing = 0 AND id = ?";
        try (SqlStmt stmt2 = DB.prepare(sql)) {
          stmt2.setInt(1, id);
          if (stmt2.update() != 1) {
            continue;
          }
        }
        processedIDs.add(id);

        int starID = res.getInt(2);
        int colonyID = res.getInt(3);
        int empireID = res.getInt(4);
        Integer existingBuildingID = res.getInt(5);
        Integer existingFleetID = res.getInt(6);
        String upgradeID = res.getString(7);
        DesignKind designKind = DesignKind.fromNumber(res.getInt(8));
        String designID = res.getString(9);
        float count = res.getFloat(10);
        String notes = res.getString(11);
        boolean disableNotification = (res.getInt(12) > 0);

        Star star = new StarController().getStar(starID);
        Colony colony = null;
        for (BaseColony bc : star.getColonies()) {
          if (((Colony) bc).getID() == colonyID) {
            colony = (Colony) bc;
          }
        }

        try {
          db.processBuildRequest(
              id, star, colony, empireID, existingBuildingID, existingFleetID, upgradeID,
              designKind, designID, count, notes, disableNotification);
        } catch (Exception e) {
          log.error("Error processing build-complete event!", e);
        }
      }
    } catch (Exception e) {
      log.error("Error processing build-complete event!", e);
    }

    if (processedIDs.isEmpty()) {
      return;
    }

    sql = "DELETE FROM build_requests WHERE id IN ";
    for (int i = 0; i < processedIDs.size(); i++) {
      if (i == 0) {
        sql += "(";
      } else {
        sql += ", ";
      }
      sql += processedIDs.get(i);
    }
    sql += ")";
    try (SqlStmt stmt = DB.prepare(sql)) {
      stmt.update();
    } catch (Exception e) {
      log.error("Error processing build-complete event!", e);
      // TODO: errors?
    }
  }

  @Nullable
  public Fleet processImmediateBuildRequest(
      Star star, Colony colony, int empireID, String designID, DesignKind designKind,  float count,
      String notes, @Nullable Integer existingBuildingID, @Nullable Integer existingFleetID,
      String upgradeID) throws RequestException {
    Fleet fleet = null;
    if (designKind == DesignKind.BUILDING) {
      db.processBuildingBuild(star, colony, existingBuildingID, designID, notes);
    } else {
      fleet = db.processFleetBuild(
          star, empireID, existingFleetID, upgradeID, designID, count, notes);
    }
    return fleet;
  }

  private class DataBase extends BaseDataBase {
    public DataBase() {}
    public DataBase(Transaction t) { super(t); }

    public void processBuildRequest(
        int buildRequestID, Star star, Colony colony, int empireID, Integer existingBuildingID,
        Integer existingFleetID, String upgradeID, DesignKind designKind, String designID,
        float count, String notes, boolean disableNotification) throws RequestException {
      Simulation sim = new Simulation();
      sim.simulate(star);

      Fleet fleet = processImmediateBuildRequest(
          star, colony, empireID, designID, designKind, count, notes, existingBuildingID,
          existingFleetID, upgradeID);

      sim.simulate(star); // simulate again to re-calculate the end times
      new StarController(getTransaction()).update(star);

      if (!disableNotification) {
        Messages.SituationReport.Builder sitrep_pb = Messages.SituationReport.newBuilder();
        sitrep_pb.setRealm(Configuration.i.getRealmName());
        sitrep_pb.setEmpireKey(Integer.toString(empireID));
        sitrep_pb.setReportTime(DateTime.now().getMillis() / 1000);
        sitrep_pb.setStarKey(star.getKey());
        sitrep_pb.setPlanetIndex(colony.getPlanetIndex());
        Messages.SituationReport.BuildCompleteRecord.Builder build_complete_pb =
            Messages.SituationReport.BuildCompleteRecord.newBuilder();
        build_complete_pb.setBuildKind(Messages.BuildRequest.BUILD_KIND.valueOf(designKind.getValue()));
        build_complete_pb.setBuildRequestKey(Integer.toString(buildRequestID));
        build_complete_pb.setDesignId(designID);
        build_complete_pb.setCount(Math.round(count));
        sitrep_pb.setBuildCompleteRecord(build_complete_pb);
        if (star.getCombatReport() != null && fleet != null) {
          Messages.SituationReport.FleetUnderAttackRecord.Builder fleet_under_attack_pb =
              Messages.SituationReport.FleetUnderAttackRecord.newBuilder();
          fleet_under_attack_pb.setCombatReportKey(star.getCombatReport().getKey());
          fleet_under_attack_pb.setFleetDesignId(fleet.getDesignID());
          fleet_under_attack_pb.setFleetKey(fleet.getKey());
          fleet_under_attack_pb.setNumShips(fleet.getNumShips());
          sitrep_pb.setFleetUnderAttackRecord(fleet_under_attack_pb);
        }

        new SituationReportController(getTransaction()).saveSituationReport(sitrep_pb.build());
      }
    }

    private Fleet processFleetBuild(
        Star star, int empireID, Integer existingFleetID, String upgradeID,
        String designID, float count, String notes) throws RequestException {
      Fleet fleet;

      Empire empire = new EmpireController(getTransaction()).getEmpire(empireID);
      if (existingFleetID != null) {
        fleet = (Fleet) star.getFleet(existingFleetID);
        if (fleet != null) {
          new FleetController(getTransaction()).addUpgrade(star, fleet, upgradeID);
        }
      } else {
        fleet = new FleetController(getTransaction()).createFleet(empire, star, designID, count);
        fleet.setNotes(notes);
        FleetMoveCompleteEvent.fireFleetArrivedEvents(star, fleet);
      }

      return fleet;
    }

    private void processBuildingBuild(
        Star star, Colony colony, Integer existingBuildingID, String designID,
        String notes) throws RequestException {
      if (existingBuildingID == null) {
        new BuildingController(getTransaction()).createBuilding(star, colony, designID, notes);
      } else {
        new BuildingController(getTransaction()).upgradeBuilding(star, colony, existingBuildingID);
      }
    }
  }
}
