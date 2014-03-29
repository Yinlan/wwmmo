package au.com.codeka.warworlds.server.data;

import java.io.ByteArrayInputStream;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.TimeZone;

import org.joda.time.ReadableInstant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This is a wrapper around a \c PreparedStatement, for ease-of-use.
 */
public class SqlStmt implements AutoCloseable {
    private final Logger log = LoggerFactory.getLogger(SqlStmt.class);
    private static Calendar sUTC;

    static {
        sUTC = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
    }

    private Connection mConn;
    private PreparedStatement mStmt;
    private boolean mAutoCloseConnection;
    private String mSql;
    private ArrayList<Object> mParameters;
    private ArrayList<ResultSet> mResultSets;
    private boolean mWasStatementLogged;

    public SqlStmt(Connection conn, String sql, PreparedStatement stmt, boolean autoCloseConnection) {
        mConn = conn;
        mStmt = stmt;
        mSql = sql;
        mAutoCloseConnection = autoCloseConnection;
        mParameters = new ArrayList<Object>();
        mResultSets = new ArrayList<ResultSet>();
        mWasStatementLogged = false;
    }

    public void setInt(int position, int value) throws SQLException {
        mStmt.setInt(position, value);
        saveParameter(position, value);
    }
    public void setInt(int position, Integer value) throws SQLException {
        if (value == null) {
            setNull(position);
        } else {
            mStmt.setInt(position, value);
            saveParameter(position, value);
        }
    }
    public void setLong(int position, long value) throws SQLException {
        mStmt.setLong(position, value);
        saveParameter(position, value);
    }
    public void setLong(int position, Long value) throws SQLException {
        if (value == null) {
            setNull(position);
        } else {
            mStmt.setLong(position, value);
            saveParameter(position, value);
        }
    }
    public void setDouble(int position, double value) throws SQLException {
        mStmt.setDouble(position, value);
        saveParameter(position, value);
    }
    public void setDouble(int position, Double value) throws SQLException {
        if (value == null) {
            setNull(position);
        } else {
            mStmt.setDouble(position, value);
            saveParameter(position, value);
        }
    }
    public void setString(int position, String value) throws SQLException {
        mStmt.setString(position, value);
        saveParameter(position, value);
    }
    public void setDateTime(int position, ReadableInstant value) throws SQLException {
        if (value == null) {
            setNull(position);
        } else {
            mStmt.setTimestamp(position, new Timestamp(value.getMillis()), sUTC);
            saveParameter(position, value);
        }
    }
    public void setBlob(int position, byte[] blob) throws SQLException {
        mStmt.setBlob(position, new ByteArrayInputStream(blob));
        saveParameter(position, "<BLOB>");
    }
    public void setNull(int position) throws SQLException {
        mStmt.setNull(position, Types.NULL);
        saveParameter(position, "<NULL>");
    }

    private void saveParameter(int position, Object value) {
        int index = position - 1;
        while (mParameters.size() < position) {
            mParameters.add(null);
        }
        mParameters.set(index, value);
    }

    /**
     * Execute an 'update' query. That is, anything but "SELECT".
     */
    public int update() throws SQLException {
        logStatement();
        return mStmt.executeUpdate();
    }

    public int getAutoGeneratedID() throws SQLException {
        ResultSet rs = null;
        try {
            rs = mStmt.getGeneratedKeys();
            if (rs.next()) {
                return rs.getInt(1);
            }
        } finally {
            if (rs != null) {
                rs.close();
            }
        }

        throw new SQLException("No auto-generated ID available.");
    }

    @SuppressWarnings("unchecked")
    public <T> T selectFirstValue(Class<T> type) throws SQLException {
        logStatement();

        ResultSet rs = null;
        try {
            rs = mStmt.executeQuery();
            if (rs.next()) {
                return (T) rs.getObject(1);
            }
            return null;
        } finally {
            rs.close();
        }
    }

    public ResultSet select() throws SQLException {
        logStatement();

        ResultSet rs = mStmt.executeQuery();
        mResultSets.add(rs);
        return rs;
    }

    private void logStatement() {
        if (mWasStatementLogged) {
            return;
        }
        mWasStatementLogged = true;

        if (log.isInfoEnabled()) {
            StringBuffer sb = new StringBuffer();
            sb.append(mSql);
            sb.append(System.lineSeparator());
            for (Object obj : mParameters) {
                sb.append(String.format("? = %s", obj));
                sb.append(System.lineSeparator());
            }
            log.info(sb.toString());
        }
    }

    @Override
    public void close() throws Exception {
        logStatement();

        for (ResultSet rs : mResultSets) {
            rs.close();
        }
        mStmt.close();
        if (mAutoCloseConnection) {
            mConn.close();
        }
    }
}
