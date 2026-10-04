package com.esg.investor;

import com.esg.exception.ApiException;
import com.esg.exception.FieldValidationException;
import com.esg.files.FileService;
import com.esg.investor.InvestorDtos.AccessEntry;
import com.esg.investor.InvestorDtos.DocumentRequest;
import com.esg.investor.InvestorDtos.Person;
import com.esg.investor.InvestorDtos.Room;
import com.esg.investor.InvestorDtos.RoomDocument;
import com.esg.investor.InvestorDtos.RoomRequest;
import com.esg.security.CurrentUser;
import java.io.IOException;
import java.io.InputStream;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Data rooms for ESG due diligence and investor relations (WP-40, D35 (4); TRD DataRoomService). Managers
 * (investor:share) create rooms, add investors and documents and read the access log; an investor sees the open,
 * unexpired rooms they are a member of. Every view and download is logged; PDFs are watermarked with the viewer.
 */
@Service
public class DataRoomService {

    private final JdbcTemplate jdbc;
    private final InvestorSupport support;
    private final FileService files;
    private final SharedReportService shared;
    private final TransactionTemplate logTx;

    public DataRoomService(JdbcTemplate jdbc, InvestorSupport support, FileService files, SharedReportService shared, PlatformTransactionManager txManager) {
        this.jdbc = jdbc;
        this.support = support;
        this.files = files;
        this.shared = shared;
        this.logTx = new TransactionTemplate(txManager);
        this.logTx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public List<Room> list() {
        String where = support.manager() ? "where r.deleted_at is null"
                : "where r.deleted_at is null and r.status = 'open' and r.expires_on >= ? and exists (select 1 from investor.data_room_members m "
                    + "where m.room_id = r.id and m.user_id = ?)";
        Object[] args = support.manager() ? new Object[0] : new Object[] {Date.valueOf(support.today()), CurrentUser.access().userId()};
        return jdbc.query("select r.* from investor.data_rooms r " + where + " order by r.status, r.expires_on", (rs, i) -> room(rs), args);
    }

    /** A room as its viewer may see it; opening it as an investor is logged. */
    @Transactional
    public Room open(UUID id) {
        Room r = load(id);
        if (!support.manager()) {
            requireAccess(r);
            log(id, null, "view", false);
        }
        return r;
    }

    @Transactional
    public Room create(RoomRequest r) {
        check(r);
        UUID id = UUID.randomUUID();
        jdbc.update("insert into investor.data_rooms (id, name, purpose, description, expires_on, status, watermark) values (?, ?, ?, ?, ?, ?, ?)", id,
                r.name().trim(), r.purpose(), ScorecardService.blank(r.description()), Date.valueOf(r.expiresOn()), r.status(), r.watermark());
        members(id, r.memberIds());
        return load(id);
    }

    @Transactional
    public Room update(UUID id, RoomRequest r) {
        load(id);
        if (r.version() == null) {
            throw FieldValidationException.of("version", "is required");
        }
        check(r);
        int n = jdbc.update("update investor.data_rooms set name = ?, purpose = ?, description = ?, expires_on = ?, status = ?, watermark = ?, "
                + "version = version + 1 where id = ? and version = ? and deleted_at is null", r.name().trim(), r.purpose(),
                ScorecardService.blank(r.description()), Date.valueOf(r.expiresOn()), r.status(), r.watermark(), id, r.version());
        if (n == 0) {
            throw ApiException.conflict("ESG-409-VERSION", "This room was changed by someone else. Reload and try again.");
        }
        members(id, r.memberIds());
        return load(id);
    }

    @Transactional
    public void delete(UUID id) {
        load(id);
        jdbc.update("update investor.data_rooms set deleted_at = now(), status = 'closed', version = version + 1 where id = ?", id);
    }

    @Transactional
    public Room addDocument(UUID roomId, DocumentRequest r) throws IOException {
        Room room = load(roomId);
        if (!Boolean.TRUE.equals(jdbc.queryForObject("select exists (select 1 from files.file_metadata where id = ?)", Boolean.class, r.fileId()))) {
            throw FieldValidationException.of("fileId", "upload the document first");
        }
        if (room.watermark()) {
            FileService.Download f = files.open(r.fileId(), null);
            if ("application/pdf".equals(f.contentType())) {
                try (InputStream in = f.stream()) {
                    if (!Watermarker.stampable(in.readAllBytes())) {
                        throw FieldValidationException.of("fileId", "this PDF can't be watermarked (damaged or password-protected); upload a readable copy");
                    }
                }
            }
        }
        jdbc.update("insert into investor.data_room_documents (room_id, file_id, title) values (?, ?, ?) on conflict (room_id, file_id) "
                + "do update set title = excluded.title, removed_at = null, version = investor.data_room_documents.version + 1", roomId, r.fileId(),
                r.title().trim());
        jdbc.update("update files.file_metadata set resource_type = 'data_room', resource_id = ? where id = ? and resource_id is null", roomId, r.fileId());
        return load(roomId);
    }

    @Transactional
    public Room removeDocument(UUID roomId, UUID documentId) {
        load(roomId);
        if (jdbc.update("update investor.data_room_documents set removed_at = now(), version = version + 1 where id = ? and room_id = ? and removed_at is null",
                documentId, roomId) == 0) {
            throw ApiException.notFound("Document");
        }
        return load(roomId);
    }

    /** A document's content: for an investor, only from a room they may open, logged and (PDFs) watermarked. */
    @Transactional
    public SharedReportService.Download download(UUID roomId, UUID documentId) throws IOException {
        Room r = load(roomId);
        if (!support.manager()) {
            requireAccess(r);
        }
        RoomDocument d = r.documents().stream().filter(x -> x.id().equals(documentId)).findFirst().orElseThrow(() -> ApiException.notFound("Document"));
        FileService.Download f = files.open(d.fileId(), null);
        byte[] content;
        try (InputStream in = f.stream()) {
            content = in.readAllBytes();
        }
        boolean stamp = r.watermark() && "application/pdf".equals(f.contentType());
        if (stamp) {
            content = Watermarker.stampOrRefuse(content, shared.stamp().replace("Shared with", "Data room " + r.name() + " · downloaded by"));
        }
        if (!support.manager()) {
            log(roomId, documentId, "download", stamp);
        }
        return new SharedReportService.Download(content, f.fileName(), f.contentType());
    }

    public List<AccessEntry> accessLog(UUID roomId) {
        load(roomId);
        return jdbc.query("select l.created_at, u.first_name || ' ' || u.last_name, u.email, l.action, d.title, l.watermarked from investor.data_room_access_log l "
                + "left join auth.users u on u.id = l.user_id left join investor.data_room_documents d on d.id = l.document_id where l.room_id = ? "
                + "order by l.created_at desc limit 500", (rs, i) -> new AccessEntry(rs.getTimestamp(1).toInstant(), rs.getString(2), rs.getString(3),
                rs.getString(4), rs.getString(5), rs.getBoolean(6)), roomId);
    }

    // ---------------------------------------------------------------- internals

    private Room load(UUID id) {
        return jdbc.query("select * from investor.data_rooms where id = ? and deleted_at is null", (rs, i) -> room(rs), id).stream().findFirst()
                .orElseThrow(() -> ApiException.notFound("Data room"));
    }

    /** 404 for rooms the investor isn't in; 403 (logged) for their own room once closed or expired. */
    private void requireAccess(Room r) {
        UUID me = CurrentUser.access().userId();
        if (!Boolean.TRUE.equals(jdbc.queryForObject("select exists (select 1 from investor.data_room_members where room_id = ? and user_id = ?)",
                Boolean.class, r.id(), me))) {
            throw ApiException.notFound("Data room");
        }
        if (!"open".equals(r.status()) || r.expired()) {
            log(r.id(), null, "refused", false);
            throw ApiException.forbidden("This data room is " + (r.expired() ? "past its expiry date (" + r.expiresOn() + ")" : "closed") + ".");
        }
    }

    /** Logged in its own transaction, so a refusal is recorded even though the request fails. */
    private void log(UUID roomId, UUID documentId, String action, boolean watermarked) {
        UUID me = CurrentUser.access().userId();
        logTx.executeWithoutResult(s -> jdbc.update("insert into investor.data_room_access_log (room_id, document_id, user_id, action, watermarked) "
                + "values (?, ?, ?, ?, ?)", roomId, documentId, me, action, watermarked));
    }

    private void check(RoomRequest r) {
        if (r.expiresOn().isBefore(support.today())) {
            throw FieldValidationException.of("expiresOn", "can't be in the past");
        }
        Set<UUID> investors = new LinkedHashSet<>(support.investors().stream().map(Person::id).toList());
        for (int i = 0; i < r.memberIds().size(); i++) {
            if (!investors.contains(r.memberIds().get(i))) {
                throw FieldValidationException.of("memberIds", "members must be investor users of your organisation");
            }
        }
    }

    private void members(UUID roomId, List<UUID> ids) {
        String[] keep = ids.stream().distinct().map(UUID::toString).toArray(String[]::new);
        jdbc.update("delete from investor.data_room_members where room_id = ? and not (user_id = any(?::uuid[]))", roomId, keep);
        for (String id : keep) {
            jdbc.update("insert into investor.data_room_members (room_id, user_id) values (?, ?::uuid) on conflict (room_id, user_id) do nothing", roomId, id);
        }
    }

    private Room room(ResultSet rs) throws SQLException {
        UUID id = rs.getObject("id", UUID.class);
        LocalDate expires = rs.getObject("expires_on", LocalDate.class);
        List<Person> members = jdbc.query("select u.id, u.first_name || ' ' || u.last_name, u.email from investor.data_room_members m join auth.users u "
                + "on u.id = m.user_id where m.room_id = ? order by 2", (r, i) -> new Person(r.getObject(1, UUID.class), r.getString(2), r.getString(3)), id);
        List<RoomDocument> docs = jdbc.query("select d.id, d.file_id, d.title, f.original_name, f.content_type, f.size_bytes, d.created_at "
                + "from investor.data_room_documents d join files.file_metadata f on f.id = d.file_id where d.room_id = ? and d.removed_at is null "
                + "order by d.title", (r, i) -> new RoomDocument(r.getObject(1, UUID.class), r.getObject(2, UUID.class), r.getString(3), r.getString(4),
                r.getString(5), (Long) r.getObject(6), r.getTimestamp(7).toInstant()), id);
        return new Room(id, rs.getString("name"), rs.getString("purpose"), rs.getString("description"), expires, rs.getString("status"),
                rs.getBoolean("watermark"), expires.isBefore(support.today()), support.manager() ? members.stream().map(Person::id).toList() : List.of(),
                support.manager() ? members : List.of(), docs, rs.getLong("version"));
    }
}
