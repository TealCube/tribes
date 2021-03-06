/**
 * The MIT License
 * Copyright (c) 2015 Teal Cube Games
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package com.tealcube.minecraft.bukkit.tribes.storage;

import com.tealcube.minecraft.bukkit.facecore.logging.PluginLogger;
import com.tealcube.minecraft.bukkit.facecore.utilities.IOUtils;
import com.tealcube.minecraft.bukkit.kern.io.CloseableRegistry;
import com.tealcube.minecraft.bukkit.shade.apache.commons.lang3.math.NumberUtils;
import com.tealcube.minecraft.bukkit.shade.google.common.base.Preconditions;
import com.tealcube.minecraft.bukkit.shade.google.common.base.Splitter;
import com.tealcube.minecraft.bukkit.tribes.TribesPlugin;
import com.tealcube.minecraft.bukkit.tribes.data.Cell;
import com.tealcube.minecraft.bukkit.tribes.data.Member;
import com.tealcube.minecraft.bukkit.tribes.data.Tribe;
import com.tealcube.minecraft.bukkit.tribes.math.Vec2;
import com.tealcube.minecraft.bukkit.tribes.math.Vec3f;

import java.io.File;
import java.sql.*;
import java.util.*;
import java.util.logging.Level;

public final class SqliteDataStorage implements DataStorage {

    private static final String TR_CELLS_CREATE = "CREATE TABLE IF NOT EXISTS tr_cells (world TEXT NOT NULL," +
            "x INTEGER NOT NULL, z INTEGER NOT NULL, owner TEXT, PRIMARY KEY (world, x, z))";
    private static final String TR_MEMBERS_CREATE = "CREATE TABLE IF NOT EXISTS tr_members (id TEXT PRIMARY " +
            "KEY, score INTEGER NOT NULL, tribe TEXT, rank TEXT, pvpstate INTEGER NOT NULL, partnerid TEXT)";
    private static final String TR_TRIBES_CREATE = "CREATE TABLE IF NOT EXISTS tr_tribes (id TEXT PRIMARY " +
            "KEY, owner TEXT NOT NULL, name TEXT NOT NULL UNIQUE, level INTEGER NOT NULL, home TEXT NOT NULL)";
    private final PluginLogger pluginLogger;
    private boolean initialized;
    private TribesPlugin plugin;
    private File file;

    public SqliteDataStorage(TribesPlugin plugin) {
        this.plugin = plugin;
        this.pluginLogger = new PluginLogger(new File(plugin.getDataFolder(), "logs/sqlite.log"));
        this.initialized = false;
        IOUtils.createDirectory(new File(plugin.getDataFolder(), "db"));
        this.file = new File(plugin.getDataFolder(), "db/tribes.db");
    }

    private void createTable() throws SQLException {
        CloseableRegistry registry = new CloseableRegistry();
        Connection connection = registry.register(getConnection());

        if (connection == null) {
            return;
        }

        Statement statement = registry.register(connection.createStatement());
        statement.executeUpdate(TR_CELLS_CREATE);
        statement.executeUpdate(TR_MEMBERS_CREATE);
        statement.executeUpdate(TR_TRIBES_CREATE);

        registry.closeQuietly();
    }

    private boolean tryQuery(Connection c, String query) {
        CloseableRegistry registry = new CloseableRegistry();
        try {
            Statement statement = registry.register(c.createStatement());
            if (statement != null) {
                statement.executeQuery(query);
            }
            return true;
        } catch (SQLException e) {
            return false;
        } finally {
            registry.closeQuietly();
        }
    }

    @Override
    public void initialize() {
        if (initialized) {
            return;
        }

        try {
            createTable();
            initialized = true;
            plugin.getPluginLogger().log(Level.INFO, "sqlite initialized");
        } catch (SQLException ex) {
            plugin.getPluginLogger().log(Level.INFO, "unable to setup sqlite");
        }
    }

    @Override
    public void shutdown() {
        // don't do anything
    }

    @Override
    public Set<Cell> loadCells() {
        Set<Cell> cells = new HashSet<>();
        Preconditions.checkState(initialized, "must be initialized");
        String query = "SELECT * FROM tr_cells";
        CloseableRegistry registry = new CloseableRegistry();
        try {
            Connection c = registry.register(getConnection());
            Statement s = registry.register(c.createStatement());
            ResultSet rs = registry.register(s.executeQuery(query));
            while (rs.next()) {
                String worldName = rs.getString("world");
                int x = rs.getInt("x");
                int z = rs.getInt("z");
                Vec2 vec2 = Vec2.fromCoordinates(worldName, x, z);
                String ownerString = rs.getString("owner");
                if (ownerString == null) {
                    cells.add(new Cell(vec2));
                } else {
                    UUID owner = UUID.fromString(ownerString);
                    cells.add(new Cell(vec2, owner));
                }
            }
        } catch (Exception e) {
            pluginLogger.log("unable to load cells: " + e.getMessage());
        } finally {
            registry.closeQuietly();
        }
        return cells;
    }

    @Override
    public Set<Cell> loadCells(Iterable<Vec2> vec2s) {
        Set<Cell> cells = new HashSet<>();
        Preconditions.checkState(initialized, "must be initialized");
        String query = "SELECT * FROM tr_cells WHERE world=? AND x=? AND z=?";
        CloseableRegistry registry = new CloseableRegistry();
        try {
            Connection c = registry.register(getConnection());
            PreparedStatement statement = registry.register(c.prepareStatement(query));
            for (Vec2 vec : vec2s) {
                statement.setString(1, vec.getWorld().getName());
                statement.setInt(2, vec.getX());
                statement.setInt(3, vec.getZ());
                ResultSet rs = registry.register(statement.executeQuery());
                while (rs.next()) {
                    String worldName = rs.getString("world");
                    int x = rs.getInt("x");
                    int z = rs.getInt("z");
                    Vec2 vec2 = Vec2.fromCoordinates(worldName, x, z);
                    String ownerString = rs.getString("owner");
                    if (ownerString == null) {
                        cells.add(new Cell(vec2));
                    } else {
                        UUID owner = UUID.fromString(ownerString);
                        cells.add(new Cell(vec2, owner));
                    }
                }
            }
        } catch (Exception e) {
            pluginLogger.log("unable to load cells: " + e.getMessage());
        } finally {
            registry.closeQuietly();
        }
        return cells;
    }

    @Override
    public Set<Cell> loadCells(Vec2... vec2s) {
        return loadCells(Arrays.asList(vec2s));
    }

    @Override
    public void saveCells(Iterable<Cell> cellIterable) {
        Preconditions.checkState(initialized, "must be initialized");
        Preconditions.checkNotNull(cellIterable, "cellIterable cannot be null");
        String query = "REPLACE INTO tr_cells (world, x, z, owner) VALUES (?,?,?,?)";
        CloseableRegistry registry = new CloseableRegistry();
        try {
            Connection c = registry.register(getConnection());
            PreparedStatement statement = registry.register(c.prepareStatement(query));
            for (Cell cell : cellIterable) {
                statement.setString(1, cell.getLocation().getWorld().getName());
                statement.setInt(2, cell.getLocation().getX());
                statement.setInt(3, cell.getLocation().getZ());
                if (cell.getOwner() == null) {
                    statement.setNull(4, Types.VARCHAR);
                } else {
                    statement.setString(4, cell.getOwner().toString());
                }
                statement.executeUpdate();
            }
        } catch (SQLException e) {
            pluginLogger.log("unable to save cells: " + e.getMessage());
        } finally {
            registry.closeQuietly();
        }
    }

    @Override
    public List<Member> loadMembers() {
        List<Member> members = new ArrayList<>();
        Preconditions.checkState(initialized, "must be initialized");
        String query = "SELECT * FROM tr_members ORDER BY score DESC";
        CloseableRegistry registry = new CloseableRegistry();
        try {
            Connection connection = registry.register(getConnection());
            Statement statement = registry.register(connection.createStatement());
            ResultSet resultSet = registry.register(statement.executeQuery(query));
            while (resultSet.next()) {
                Member member = new Member(UUID.fromString(resultSet.getString("id")));
                member.setScore(resultSet.getInt("score"));
                String tribeString = resultSet.getString("tribe");
                if (tribeString != null) {
                    member.setTribe(UUID.fromString(tribeString));
                } else {
                    member.setTribe(null);
                }
                member.setRank(Tribe.Rank.fromString(resultSet.getString("rank")));
                member.setPvpState(Member.PvpState.values()[resultSet.getInt("pvpstate")]);
                members.add(member);
            }
        } catch (SQLException e) {
            pluginLogger.log("unable to load members:" + e.getMessage());
        } finally {
            registry.closeQuietly();
        }
        return members;
    }

    @Override
    public List<Member> loadMembers(Iterable<UUID> uuids) {
        List<Member> members = new ArrayList<>();
        Preconditions.checkState(initialized, "must be initialized");
        String query = "SELECT * FROM tr_members WHERE id=? ORDER BY score DESC";
        CloseableRegistry registry = new CloseableRegistry();
        try {
            Connection c = registry.register(getConnection());
            PreparedStatement statement = registry.register(c.prepareStatement(query));
            for (UUID uuid : uuids) {
                statement.setString(1, uuid.toString());
                ResultSet resultSet = registry.register(statement.executeQuery());
                while (resultSet.next()) {
                    Member member = new Member(UUID.fromString(resultSet.getString("id")));
                    member.setScore(resultSet.getInt("score"));
                    String tribe = resultSet.getString("tribe");
                    if (tribe != null) {
                        member.setTribe(UUID.fromString(tribe));
                    } else {
                        member.setTribe(null);
                    }
                    member.setRank(Tribe.Rank.fromString(resultSet.getString("rank")));
                    member.setPvpState(Member.PvpState.values()[resultSet.getInt("pvpstate")]);
                    members.add(member);
                }
            }
        } catch (Exception e) {
            pluginLogger.log("unable to load members: " + e.getMessage());
        } finally {
            registry.closeQuietly();
        }
        return members;
    }

    @Override
    public List<Member> loadMembers(UUID... uuids) {
        return loadMembers(Arrays.asList(uuids));
    }

    @Override
    public void saveMembers(Iterable<Member> memberIterable) {
        Preconditions.checkNotNull(memberIterable, "memberIterable cannot be null");
        Preconditions.checkState(initialized, "must be initialized");
        CloseableRegistry registry = new CloseableRegistry();
        String query = "REPLACE INTO tr_members (id, score, tribe, rank, pvpstate) VALUES (?,?,?,?,?)";
        try {
            Connection connection = registry.register(getConnection());
            PreparedStatement statement = registry.register(connection.prepareStatement(query));
            for (Member member : memberIterable) {
                statement.setString(1, member.getUniqueId().toString());
                statement.setInt(2, member.getScore());
                if (member.getTribe() == null) {
                    statement.setNull(3, Types.VARCHAR);
                } else {
                    statement.setString(3, member.getTribe().toString());
                }
                statement.setString(4, member.getRank() != null ? member.getRank().name() : Tribe.Rank.GUEST.name());
                statement.setInt(5, member.getPvpState().ordinal());
                statement.executeUpdate();
            }
        } catch (SQLException e) {
            pluginLogger.log("unable to save members: " + e.getMessage());
        } finally {
            registry.closeQuietly();
        }
    }

    @Override
    public List<Tribe> loadTribes() {
        List<Tribe> tribes = new ArrayList<>();
        Preconditions.checkState(initialized, "must be initialized");
        String query = "SELECT * FROM tr_tribes";
        CloseableRegistry registry = new CloseableRegistry();
        try {
            Connection connection = registry.register(getConnection());
            Statement statement = registry.register(connection.createStatement());
            ResultSet resultSet = registry.register(statement.executeQuery(query));
            while (resultSet.next()) {
                Tribe tribe = new Tribe(UUID.fromString(resultSet.getString("id")));
                tribe.setOwner(UUID.fromString(resultSet.getString("owner")));
                tribe.setName(resultSet.getString("name"));
                tribe.setLevel(Tribe.Level.values()[resultSet.getInt("level")]);
                String home = resultSet.getString("home");
                List<String> lHome = Splitter.on(":").omitEmptyStrings().trimResults().splitToList(home);
                tribe.setHome(Vec3f.fromCoordinates(lHome.get(0), NumberUtils.toInt(lHome.get(1)),
                        NumberUtils.toInt(lHome.get(2)), NumberUtils.toInt(lHome.get(3)),
                        NumberUtils.toFloat(lHome.get(4)), NumberUtils.toFloat(lHome.get(5))));
                tribe.setValidated(true);
                tribes.add(tribe);
            }
        } catch (SQLException e) {
            pluginLogger.log("unable to load tribes: " + e.getMessage());
        } finally {
            registry.closeQuietly();
        }
        return tribes;
    }

    @Override
    public List<Tribe> loadTribes(Iterable<UUID> uuids) {
        List<Tribe> tribes = new ArrayList<>();
        Preconditions.checkState(initialized, "must be initialized");
        String query = "SELECT * FROM tr_tribes WHERE id=?";
        CloseableRegistry registry = new CloseableRegistry();
        try {
            Connection connection = registry.register(getConnection());
            PreparedStatement statement = registry.register(connection.prepareStatement(query));
            for (UUID uuid : uuids) {
                statement.setString(1, uuid.toString());
                ResultSet resultSet = registry.register(statement.executeQuery());
                while (resultSet.next()) {
                    Tribe tribe = new Tribe(UUID.fromString(resultSet.getString("id")));
                    tribe.setOwner(UUID.fromString(resultSet.getString("owner")));
                    tribe.setName(resultSet.getString("name"));
                    tribe.setLevel(Tribe.Level.values()[resultSet.getInt("level")]);
                    String home = resultSet.getString("home");
                    List<String> lHome = Splitter.on(":").omitEmptyStrings().trimResults().splitToList(home);
                    tribe.setHome(Vec3f.fromCoordinates(lHome.get(0), NumberUtils.toInt(lHome.get(1)),
                            NumberUtils.toInt(lHome.get(2)), NumberUtils.toInt(lHome.get(3)),
                            NumberUtils.toFloat(lHome.get(4)), NumberUtils.toFloat(lHome.get(5))));
                    tribe.setValidated(true);
                    tribes.add(tribe);
                }
            }
        } catch (SQLException e) {
            pluginLogger.log("unable to load tribes: " + e.getMessage());
        } finally {
            registry.closeQuietly();
        }
        return tribes;
    }

    @Override
    public List<Tribe> loadTribes(UUID... uuids) {
        return loadTribes(Arrays.asList(uuids));
    }

    @Override
    public void saveTribes(Iterable<Tribe> tribeIterable) {
        Preconditions.checkNotNull(tribeIterable);
        Preconditions.checkState(initialized, "must be initialized");
        String query = "REPLACE INTO tr_tribes (id, owner, name, level, home) VALUES (?,?,?,?,?)";
        CloseableRegistry registry = new CloseableRegistry();
        try {
            Connection connection = registry.register(getConnection());
            PreparedStatement statement = registry.register(connection.prepareStatement(query));
            for (Tribe tribe : tribeIterable) {
                if (!tribe.isValidated()) {
                    plugin.debug("not saving tribe " + tribe.getName() + " due to not being validated");
                    continue;
                }
                statement.setString(1, tribe.getUniqueId().toString());
                if (tribe.getOwner() == null) {
                    statement.setNull(2, Types.VARCHAR);
                } else {
                    statement.setString(2, tribe.getOwner().toString());
                }
                statement.setString(3, tribe.getName());
                statement.setInt(4, tribe.getLevel().ordinal());
                statement.setString(5, tribe.getHome().toString());
                statement.executeUpdate();
            }
        } catch (SQLException e) {
            pluginLogger.log("unable to save tribes: " + e.getMessage());
        } finally {
            registry.closeQuietly();
        }
    }

    private String getConnectionURI() {
        return "jdbc:sqlite:" + file.getAbsolutePath();
    }

    private Connection getConnection() {
        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException e) {
            return null;
        }
        try {
            return DriverManager.getConnection(getConnectionURI());
        } catch (SQLException e) {
            e.printStackTrace();
            return null;
        }
    }

}
