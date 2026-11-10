package com.campus.lostfound.modules.item.service;

import com.campus.lostfound.common.error.BusinessException;
import com.campus.lostfound.common.error.ErrorCode;
import com.campus.lostfound.config.AppProperties;
import com.campus.lostfound.modules.item.dto.ItemViews.Image;
import com.campus.lostfound.modules.item.mapper.ImageMapper;
import com.campus.lostfound.modules.item.mapper.ImageMapper.ImageRow;
import com.campus.lostfound.modules.item.mapper.ImageMapper.NewImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.imageio.ImageIO;
import javax.imageio.stream.MemoryCacheImageInputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

@Service
public class ImageService {
    private static final Logger log = LoggerFactory.getLogger(ImageService.class);
    public static final int MAX_BYTES = 2 * 1024 * 1024;
    private final ImageMapper mapper;
    private final Path directory;

    public ImageService(ImageMapper mapper, AppProperties properties) {
        this.mapper = mapper;
        this.directory = Path.of(properties.storage().uploadDir()).toAbsolutePath().normalize();
    }

    @Transactional
    public Image upload(long userId, MultipartFile file) {
        if (file.isEmpty()) throw new BusinessException(ErrorCode.VALIDATION_ERROR, "请选择图片");
        if (file.getSize() > MAX_BYTES) throw new BusinessException(ErrorCode.IMAGE_TOO_LARGE);
        try {
            byte[] content = file.getBytes();
            if (content.length > MAX_BYTES) throw new BusinessException(ErrorCode.IMAGE_TOO_LARGE);
            String mediaType = detect(content);
            // 只使用服务端生成的文件名，不使用客户端路径、扩展名或 MIME 声明。
            String key = UUID.randomUUID() + (mediaType.equals("image/png") ? ".png" : ".jpg");
            Files.createDirectories(directory);
            Path target = directory.resolve(key);
            Files.write(target, content, StandardOpenOption.CREATE_NEW);
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCompletion(int status) {
                    if (status != STATUS_COMMITTED) {
                        try { Files.deleteIfExists(target); }
                        catch (IOException ex) { log.error("回滚上传文件失败", ex); }
                    }
                }
            });
            NewImage image = new NewImage(userId, key, mediaType, content.length);
            mapper.insert(image);
            return view(mapper.find(image.getId()));
        } catch (IOException ex) {
            throw new IllegalStateException("图片存储失败", ex);
        }
    }

    private static String detect(byte[] content) {
        try (var input = new MemoryCacheImageInputStream(new ByteArrayInputStream(content))) {
            var readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw new BusinessException(ErrorCode.UNSUPPORTED_MEDIA_TYPE);
            var reader = readers.next();
            try {
                String format = reader.getFormatName();
                String mediaType = switch (format.toLowerCase(java.util.Locale.ROOT)) {
                    case "jpeg", "jpg" -> "image/jpeg";
                    case "png" -> "image/png";
                    default -> throw new BusinessException(ErrorCode.UNSUPPORTED_MEDIA_TYPE);
                };
                reader.setInput(input, true, true);
                // 解码前限制像素数，防止小体积文件导致过量内存分配。
                int width = reader.getWidth(0), height = reader.getHeight(0);
                if (width < 1 || height < 1 || (long) width * height > 16_000_000L) {
                    throw new BusinessException(ErrorCode.UNSUPPORTED_MEDIA_TYPE, "图片像素不能超过 1600 万");
                }
                if (reader.read(0) == null) throw new BusinessException(ErrorCode.UNSUPPORTED_MEDIA_TYPE);
                return mediaType;
            } finally { reader.dispose(); }
        } catch (IOException | IllegalArgumentException ex) {
            throw new BusinessException(ErrorCode.UNSUPPORTED_MEDIA_TYPE, "图片损坏或格式不受支持");
        }
    }

    @Transactional(readOnly = true)
    public Content read(long id, Long userId, boolean admin) {
        if (!mapper.canRead(id, userId, admin)) throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        ImageRow row = mapper.find(id);
        Path path = directory.resolve(row.storageKey()).normalize();
        if (!path.getParent().equals(directory)) throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        try {
            return new Content(row.mediaType(), Files.readAllBytes(path));
        } catch (java.nio.file.NoSuchFileException ex) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        } catch (IOException ex) {
            throw new IllegalStateException("图片读取失败", ex);
        }
    }

    /** 调用方先锁物品，再按编号锁图片；更换绑定与物品写入使用同一事务。 */
    public void replace(long itemId, long userId, List<String> requested) {
        List<String> ids = requested == null ? List.of() : requested;
        if (ids.size() > 3 || new HashSet<>(ids).size() != ids.size()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "图片最多三张且不能重复");
        }
        List<Long> parsed = new ArrayList<>();
        try {
            for (String id : ids) {
                long value = Long.parseLong(id);
                if (value < 1) throw new NumberFormatException();
                parsed.add(value);
            }
        } catch (NumberFormatException | NullPointerException ex) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "图片编号不合法");
        }
        for (long id : parsed.stream().sorted().toList()) {
            ImageRow row = mapper.lock(id);
            if (row == null || row.uploaderId() != userId) throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
            if (row.itemId() != null && row.itemId() != itemId) {
                throw new BusinessException(ErrorCode.IMAGE_ALREADY_BOUND);
            }
        }
        mapper.detach(itemId);
        for (int index = 0; index < parsed.size(); index++) mapper.bind(parsed.get(index), itemId, index + 1);
    }

    public Map<Long, List<Image>> forItems(List<Long> ids) {
        Map<Long, List<Image>> result = new HashMap<>();
        if (!ids.isEmpty()) {
            for (ImageRow row : mapper.forItems(ids)) {
                result.computeIfAbsent(row.itemId(), ignored -> new ArrayList<>()).add(view(row));
            }
        }
        return result;
    }

    private static Image view(ImageRow row) {
        return new Image(Long.toString(row.id()), "/api/v1/images/" + row.id(), row.mediaType(), row.sizeBytes());
    }

    public record Content(String mediaType, byte[] bytes) {}
}
