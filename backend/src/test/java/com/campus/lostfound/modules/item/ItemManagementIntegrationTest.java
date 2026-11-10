package com.campus.lostfound.modules.item;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.campus.lostfound.modules.account.security.AccountUserDetails;
import com.jayway.jsonpath.JsonPath;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import com.campus.lostfound.config.AppProperties;
import java.nio.file.Files;
import java.nio.file.Path;
import com.campus.lostfound.modules.item.service.ItemService;
import com.campus.lostfound.modules.item.dto.EditItemRequest;
import com.campus.lostfound.common.error.BusinessException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(properties = "app.admin.account=")
@AutoConfigureMockMvc
@EnabledIf("com.campus.lostfound.TestDatabase#configured")
class ItemManagementIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;
    @Autowired SqlSessionTemplate session;
    @Autowired AppProperties properties;
    @Autowired ItemService service;
    AccountUserDetails owner;
    AccountUserDetails other;

    @BeforeEach void fixtures() {
        owner = student();
        other = student();
    }

    @AfterEach void cleanup() throws Exception {
        for (AccountUserDetails actor : new AccountUserDetails[]{owner, other}) {
            if (actor == null) continue;
            for (String key : jdbc.queryForList("SELECT storage_key FROM images WHERE uploader_id=?", String.class, actor.userId())) {
                Files.deleteIfExists(Path.of(properties.storage().uploadDir()).resolve(key));
            }
            jdbc.update("DELETE FROM images WHERE uploader_id=?", actor.userId());
        }
        if (owner != null) {
            jdbc.update("DELETE FROM clues WHERE publisher_id=?", owner.userId());
            jdbc.update("DELETE FROM claims WHERE publisher_id=?", owner.userId());
            jdbc.update("DELETE FROM items WHERE publisher_id=?", owner.userId());
        }
        for (AccountUserDetails actor : new AccountUserDetails[]{owner, other}) {
            if (actor == null) continue;
            Long studentId = jdbc.queryForObject("SELECT student_record_id FROM users WHERE id=?", Long.class, actor.userId());
            jdbc.update("DELETE FROM users WHERE id=?", actor.userId());
            jdbc.update("DELETE FROM mock_students WHERE id=?", studentId);
        }
    }

    @Test void detectsActualPngAndJpegRatherThanFilenameOrDeclaredType() throws Exception {
        for (String format : List.of("png", "jpeg")) {
            var out = new ByteArrayOutputStream();
            ImageIO.write(new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB), format, out);
            mvc.perform(multipart("/api/v1/uploads/images")
                    .file(new MockMultipartFile("file", "../../wrong.txt", "text/plain", out.toByteArray()))
                    .with(user(owner)).with(csrf()))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.data.mediaType").value("image/" + format))
                    .andExpect(jsonPath("$.data.sizeBytes").value(out.size()));
        }
    }

    @Test void rejectsFakeEmptyOversizedAndAnonymousUploads() throws Exception {
        upload(owner, new byte[]{1, 2, 3}).andExpect(status().isUnsupportedMediaType());
        upload(owner, new byte[0]).andExpect(status().isBadRequest());
        upload(owner, new byte[2097153]).andExpect(status().is(413))
                .andExpect(jsonPath("$.code").value("IMAGE_TOO_LARGE"));
        mvc.perform(multipart("/api/v1/uploads/images")
                .file(new MockMultipartFile("file", "item.png", "image/png", png())).with(csrf()))
                .andExpect(status().isUnauthorized());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM images WHERE uploader_id=?", Long.class, owner.userId()))
                .isZero();
    }

    @Test void unboundImagesAreVisibleOnlyToTheirUploader() throws Exception {
        String id = uploaded(owner);
        mvc.perform(get("/api/v1/images/{id}", id)).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/images/{id}", id).with(user(other))).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/images/{id}", id).with(user(owner)))
                .andExpect(status().isOk()).andExpect(content().bytes(png()))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(content().contentType("image/png"));
    }

    @Test void threeImagesFollowItemVisibilityAndAreReturnedInOrder() throws Exception {
        List<String> images = List.of(uploaded(owner), uploaded(owner), uploaded(owner));
        String id = created(images);
        mvc.perform(get("/api/v1/me/items/{id}", id).with(user(owner)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.images.length()").value(3))
                .andExpect(jsonPath("$.data.images[0].id").value(images.getFirst()));
        mvc.perform(get("/api/v1/images/{id}", images.getFirst())).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/images/{id}", images.getFirst()).with(user(other))).andExpect(status().isNotFound());
        published(id);
        mvc.perform(get("/api/v1/images/{id}", images.getFirst())).andExpect(status().isOk());
        mvc.perform(get("/api/v1/items/{id}", id)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.images[2].id").value(images.get(2)));
        mvc.perform(get("/api/v1/items").param("keyword", "管理测试"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.items[0].images.length()").value(3));
        withdraw(id, 1).andExpect(status().isOk());
        mvc.perform(get("/api/v1/images/{id}", images.getFirst())).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/images/{id}", images.getFirst()).with(user(owner))).andExpect(status().isOk());
    }

    @Test void retriesWithImagesReturnSameItemAndDoNotRestoreOldImagesAfterEdit() throws Exception {
        String first = uploaded(owner), second = uploaded(owner);
        String key = UUID.randomUUID().toString();
        String body = json.writeValueAsString(Map.of("type","FOUND","category","OTHER","title","图片防重测试",
                "location","图书馆","occurredDate","2026-10-01","description","发现一个带有标识的黑色物品", "imageIds",List.of(first)));
        String id = JsonPath.read(mvc.perform(post("/api/v1/items").with(user(owner)).with(csrf())
                .header("Idempotency-Key",key).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.data.id");
        mvc.perform(post("/api/v1/items").with(user(owner)).with(csrf())
                .header("Idempotency-Key",key).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.data.id").value(id));
        edit(id, Map.of("expectedVersion",1,"imageIds",List.of(second))).andExpect(status().isOk());
        mvc.perform(post("/api/v1/items").with(user(owner)).with(csrf())
                .header("Idempotency-Key",key).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.data.id").value(id))
                .andExpect(jsonPath("$.data.version").value(2)).andExpect(jsonPath("$.data.images[0].id").value(second));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM items WHERE publisher_id=?",Long.class,owner.userId())).isEqualTo(1);
    }

    @Test void cannotBindAnotherPersonsImageAndRollsBackTheItem() throws Exception {
        String image = uploaded(other);
        create(List.of(image)).andExpect(status().isNotFound());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM items WHERE publisher_id=?", Long.class, owner.userId()))
                .isZero();
        assertThat(jdbc.queryForObject("SELECT item_id FROM images WHERE id=?", Long.class, image)).isNull();
    }

    @Test void cannotReuseBoundImagesOrSubmitDuplicateOrInvalidIds() throws Exception {
        String image = uploaded(owner);
        created(List.of(image));
        create(List.of(image)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IMAGE_ALREADY_BOUND"));
        create(List.of(image, image)).andExpect(status().isBadRequest());
        create(List.of(image, image, image, image)).andExpect(status().isBadRequest());
        create(List.of("9999999999999999999")).andExpect(status().isBadRequest());
        create(java.util.Arrays.asList((String) null)).andExpect(status().isBadRequest());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM items WHERE publisher_id=?", Long.class, owner.userId()))
                .isEqualTo(1);
    }

    @Test void editRequeuesPublishedItemAndStaleVersionCannotOverwrite() throws Exception {
        String image = uploaded(owner), id = created(List.of(image));
        published(id);
        edit(id, Map.of("expectedVersion", 1, "title", "重新审核的标题"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.version").value(2))
                .andExpect(jsonPath("$.data.status").value("PENDING_REVIEW"))
                .andExpect(jsonPath("$.data.publishedAt").isEmpty())
                .andExpect(jsonPath("$.data.images[0].id").value(image));
        mvc.perform(get("/api/v1/items/{id}", id)).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/items").param("keyword", "重新审核的标题"))
                .andExpect(jsonPath("$.data.total").value(0));
        mvc.perform(get("/api/v1/images/{id}", image)).andExpect(status().isNotFound());
        edit(id, Map.of("expectedVersion", 1, "title", "过期覆盖的标题"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("VERSION_CONFLICT"));
        assertThat(jdbc.queryForObject("SELECT title FROM items WHERE id=?", String.class, id)).isEqualTo("重新审核的标题");
    }

    @Test void imageReplacementReordersAndCanRemoveAllWithoutDeletingFiles() throws Exception {
        String first = uploaded(owner), second = uploaded(owner), id = created(List.of(first, second));
        edit(id, Map.of("expectedVersion", 1, "imageIds", List.of(second, first)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.images[0].id").value(second));
        edit(id, Map.of("expectedVersion", 2, "imageIds", List.of()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.images").isEmpty());
        assertThat(jdbc.queryForObject("SELECT item_id FROM images WHERE id=?", Long.class, first)).isNull();
        mvc.perform(get("/api/v1/images/{id}", first).with(user(owner))).andExpect(status().isOk());
    }

    @Test void invalidEditLeavesTextVersionAndBindingsUnchanged() throws Exception {
        String first = uploaded(owner), id = created(List.of(first));
        edit(id, Map.of("expectedVersion", 1, "title", "", "imageIds", List.of()))
                .andExpect(status().isBadRequest());
        edit(id, Map.of("expectedVersion", 1, "description", "请联系 demo@example.test 领取"))
                .andExpect(status().isBadRequest());
        edit(id, Map.of("expectedVersion", 1, "type", "LOST")).andExpect(status().isBadRequest());
        mvc.perform(patch("/api/v1/items/{id}", id).with(user(owner)).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"expectedVersion\":1,\"title\":null}"))
                .andExpect(status().isBadRequest());
        edit(id, Map.of("expectedVersion", 1)).andExpect(status().isBadRequest());
        edit(id, Map.of("expectedVersion", 1, "title", "合法新标题", "imageIds", List.of(uploaded(other))))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/me/items/{id}", id).with(user(owner)))
                .andExpect(jsonPath("$.data.version").value(1))
                .andExpect(jsonPath("$.data.title").value("管理测试物品"))
                .andExpect(jsonPath("$.data.images[0].id").value(first));
    }

    @Test void ownListAndChangesCannotAccessAnotherPublishersItem() throws Exception {
        String id = created(List.of());
        mvc.perform(get("/api/v1/me/items/{id}", id).with(user(other))).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/me/items").with(user(other))).andExpect(jsonPath("$.data.total").value(0));
        mvc.perform(get("/api/v1/me/items").with(user(owner)).param("status", "PENDING_REVIEW"))
                .andExpect(jsonPath("$.data.total").value(1));
        mvc.perform(patch("/api/v1/items/{id}", id).with(user(other)).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("expectedVersion", 1, "title", "越权修改"))))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/items/{id}/withdraw", id).with(user(other)).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"expectedVersion\":1}"))
                .andExpect(status().isNotFound());
    }

    @Test void pendingClaimBlocksEditButAllRejectedClaimsPermitIt() throws Exception {
        String id = created(List.of());
        claim(id, "PENDING");
        edit(id, Map.of("expectedVersion", 1, "title", "不能修改的标题"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ITEM_STATE_CONFLICT"));
        jdbc.update("UPDATE claims SET status='REJECTED', reason='核对不符' WHERE item_id=?", id);
        session.clearCache();
        edit(id, Map.of("expectedVersion", 1, "title", "驳回后修改标题"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.version").value(2));
    }

    @Test void confirmedClaimBlocksWithdrawalAndPrivateImagesRemainForParticipants() throws Exception {
        String image = uploaded(owner), id = created(List.of(image));
        claim(id, "CONFIRMED");
        withdraw(id, 1).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ITEM_ALREADY_CLAIMED"));
        edit(id, Map.of("expectedVersion", 1, "title", "不能修改的标题")).andExpect(status().isConflict());
        mvc.perform(get("/api/v1/images/{id}", image).with(user(other))).andExpect(status().isOk());
    }

    @Test void withdrawalClosesPendingClaimsAndTerminalItemCannotBeEdited() throws Exception {
        String id = created(List.of());
        published(id);
        claim(id, "PENDING");
        withdraw(id, 1).andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("OFF_SHELF"))
                .andExpect(jsonPath("$.data.closureReason").value("OWNER_WITHDRAWN"))
                .andExpect(jsonPath("$.data.version").value(2));
        assertThat(jdbc.queryForObject("SELECT status FROM claims WHERE item_id=?", String.class, id)).isEqualTo("CLOSED");
        mvc.perform(get("/api/v1/items/{id}", id)).andExpect(status().isNotFound());
        withdraw(id, 1).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("VERSION_CONFLICT"));
        withdraw(id, 2).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ITEM_STATE_CONFLICT"));
        edit(id, Map.of("expectedVersion", 2, "title", "不能修改的标题")).andExpect(status().isConflict());
    }

    @Test void concurrentEditsWithSameVersionAllowOnlyOneWinner() throws Exception {
        String id = created(List.of());
        var start = new CountDownLatch(1);
        try (var workers = Executors.newFixedThreadPool(2)) {
            var first = workers.submit(() -> concurrentEdit(id, "第一个修改标题", start));
            var second = workers.submit(() -> concurrentEdit(id, "第二个修改标题", start));
            start.countDown();
            assertThat(List.of(first.get(15, TimeUnit.SECONDS), second.get(15, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder("OK", "VERSION_CONFLICT");
        }
        assertThat(jdbc.queryForObject("SELECT version FROM items WHERE id=?", Long.class, id)).isEqualTo(2);
    }

    @Test void returnedItemCanResubmitButArchivedItemCannotChange() throws Exception {
        String id = created(List.of());
        jdbc.update("UPDATE items SET status='RETURNED', review_reason='描述需要补充' WHERE id=?", id);
        edit(id, Map.of("expectedVersion", 1, "title", "补充后的物品标题"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.reviewReason").isEmpty())
                .andExpect(jsonPath("$.data.status").value("PENDING_REVIEW"));
        jdbc.update("UPDATE items SET status='ARCHIVED', closure_reason='HANDOVER', closed_at=UTC_TIMESTAMP(6) WHERE id=?", id);
        edit(id, Map.of("expectedVersion", 2, "title", "归档后修改标题")).andExpect(status().isConflict());
        withdraw(id, 2).andExpect(status().isConflict());
    }

    @Test void lostItemWithCluesCannotEditButCanWithdraw() throws Exception {
        String image = uploaded(owner), id = created(List.of(image));
        jdbc.update("UPDATE items SET type='LOST' WHERE id=?", id);
        jdbc.update("INSERT INTO clues(lost_item_id,publisher_id,provider_id,content) VALUES (?,?,?,'测试线索的详细内容说明')",
                id, owner.userId(), other.userId());
        edit(id, Map.of("expectedVersion", 1, "title", "有线索后修改标题")).andExpect(status().isConflict());
        withdraw(id, 1).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM clues WHERE lost_item_id=?", Long.class, id)).isEqualTo(1);
        mvc.perform(get("/api/v1/images/{id}", image).with(user(other))).andExpect(status().isOk());
    }

    private String concurrentEdit(String id, String title, CountDownLatch start) throws Exception {
        start.await();
        EditItemRequest patch = new EditItemRequest();
        patch.expectedVersion = 1L;
        patch.title = title;
        try { service.edit(Long.parseLong(id), owner.userId(), patch); return "OK"; }
        catch (BusinessException ex) { return ex.getErrorCode().name(); }
    }

    private AccountUserDetails student() {
        String tag = UUID.randomUUID().toString().replace("-", "");
        jdbc.update("INSERT INTO mock_students (student_no, campus_card_no, real_name) VALUES (?, ?, '测试生')", tag, tag);
        jdbc.update("INSERT INTO users (student_record_id, nickname, email, password_hash) SELECT id,'管理测试',?,'unused' FROM mock_students WHERE student_no=?", tag+"@example.test", tag);
        return new AccountUserDetails(jdbc.queryForObject("SELECT id FROM users WHERE email=?", Long.class, tag+"@example.test"), "STUDENT", 1, null);
    }
    private byte[] png() throws Exception {
        var out = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB), "png", out);
        return out.toByteArray();
    }
    private ResultActions upload(AccountUserDetails actor, byte[] bytes) throws Exception {
        return mvc.perform(multipart("/api/v1/uploads/images")
                .file(new MockMultipartFile("file", "item.png", "image/png", bytes)).with(user(actor)).with(csrf()));
    }
    private String uploaded(AccountUserDetails actor) throws Exception {
        return JsonPath.read(upload(actor, png()).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.data.id");
    }
    private ResultActions create(List<String> ids) throws Exception {
        return mvc.perform(post("/api/v1/items").with(user(owner)).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("type","FOUND","category","OTHER","title","管理测试物品",
                        "location","图书馆","occurredDate","2026-10-01","description","发现一个带有标识的黑色物品", "imageIds",ids))));
    }
    private String created(List<String> ids) throws Exception {
        return JsonPath.read(create(ids).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.data.id");
    }
    private ResultActions edit(String id, Map<String, ?> request) throws Exception {
        return mvc.perform(patch("/api/v1/items/{id}", id).with(user(owner)).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(request)));
    }
    private ResultActions withdraw(String id, long version) throws Exception {
        return mvc.perform(post("/api/v1/items/{id}/withdraw", id).with(user(owner)).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("expectedVersion",version))));
    }
    private void published(String id) {
        jdbc.update("UPDATE items SET status='PUBLISHED', published_at=UTC_TIMESTAMP(6) WHERE id=?", id);
        session.clearCache();
    }
    private void claim(String id, String status) {
        jdbc.update("INSERT INTO claims(item_id,publisher_id,applicant_id,description,status,confirmed_at) VALUES (?,?,?,'认领测试的物品特征说明',?,IF(?='CONFIRMED',UTC_TIMESTAMP(6),NULL))",
                id, owner.userId(), other.userId(), status, status);
        session.clearCache();
    }
}
