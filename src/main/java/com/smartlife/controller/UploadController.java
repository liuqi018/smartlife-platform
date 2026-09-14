package com.smartlife.controller;

import com.smartlife.dto.Result;
import com.smartlife.dto.UserDTO;
import com.smartlife.service.IBlogService;
import com.smartlife.utils.SystemConstants;
import com.smartlife.utils.UserHolder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import javax.annotation.Resource;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("upload")
public class UploadController {
    private static final long MAX_BYTES = 5L * 1024 * 1024;
    private static final Set<String> EXTENSIONS = new HashSet<>(Arrays.asList("jpg", "jpeg", "png", "webp"));
    private static final String OWNER_KEY = "upload:owner:";
    @Resource private StringRedisTemplate stringRedisTemplate;
    @Resource private IBlogService blogService;

    @PostMapping("blog")
    public Result uploadImage(@RequestParam("file") MultipartFile image) {
        UserDTO user = UserHolder.getUser();
        if (user == null) return Result.fail("请先登录");
        String error = validate(image);
        if (error != null) return Result.fail(error);
        String relative = createName(extension(image.getOriginalFilename()));
        try {
            Path base = Paths.get(SystemConstants.IMAGE_UPLOAD_DIR).toAbsolutePath().normalize();
            Path target = base.resolve(relative.substring(1)).normalize();
            if (!target.startsWith(base)) return Result.fail("非法文件路径");
            Files.createDirectories(target.getParent());
            image.transferTo(target);
            stringRedisTemplate.opsForValue().set(OWNER_KEY + relative, user.getId().toString(), 24, TimeUnit.HOURS);
            return Result.ok(relative);
        } catch (IOException e) { throw new IllegalStateException("文件上传失败", e); }
    }

    @DeleteMapping("/blog")
    public Result deleteBlogImg(@RequestParam("name") String name) {
        UserDTO user = UserHolder.getUser();
        if (user == null) return Result.fail("请先登录");
        String relative = normalizeName(name);
        if (relative == null) return Result.fail("非法文件路径");
        String owner = stringRedisTemplate.opsForValue().get(OWNER_KEY + relative);
        boolean ownBlog = blogService.query().eq("user_id", user.getId()).like("images", relative).count() > 0;
        if (!user.getId().toString().equals(owner) && !ownBlog) return Result.fail("无权删除该图片");
        Path base = Paths.get(SystemConstants.IMAGE_UPLOAD_DIR).toAbsolutePath().normalize();
        Path target = base.resolve(relative.substring(1)).normalize();
        if (!target.startsWith(base)) return Result.fail("非法文件路径");
        try {
            Files.deleteIfExists(target);
            stringRedisTemplate.delete(OWNER_KEY + relative);
            return Result.ok();
        } catch (IOException e) { throw new IllegalStateException("文件删除失败", e); }
    }

    private String validate(MultipartFile file) {
        if (file == null || file.isEmpty()) return "文件不能为空";
        if (file.getSize() > MAX_BYTES) return "图片不能超过5MB";
        String ext = extension(file.getOriginalFilename());
        if (!EXTENSIONS.contains(ext)) return "只允许jpg/jpeg/png/webp图片";
        String mime = Optional.ofNullable(file.getContentType()).orElse("").toLowerCase(Locale.ROOT);
        if (!(ext.equals("jpg") || ext.equals("jpeg") ? mime.equals("image/jpeg") : mime.equals("image/" + ext))) return "图片类型与扩展名不匹配";
        try (InputStream in = file.getInputStream()) {
            byte[] h = new byte[12]; int n = in.read(h);
            boolean magic = ((ext.equals("jpg") || ext.equals("jpeg")) && n >= 3 && (h[0]&255)==255 && (h[1]&255)==216 && (h[2]&255)==255)
                    || (ext.equals("png") && n >= 8 && (h[0]&255)==137 && h[1]==80 && h[2]==78 && h[3]==71)
                    || (ext.equals("webp") && n >= 12 && h[0]=='R' && h[1]=='I' && h[2]=='F' && h[3]=='F' && h[8]=='W' && h[9]=='E' && h[10]=='B' && h[11]=='P');
            return magic ? null : "图片内容格式无效";
        } catch (IOException e) { return "无法读取图片"; }
    }
    private String extension(String name) { int dot = name == null ? -1 : name.lastIndexOf('.'); return dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT); }
    private String createName(String ext) { String id=UUID.randomUUID().toString(); int h=id.hashCode(); return "/blogs/"+(h&15)+"/"+((h>>4)&15)+"/"+id+"."+ext; }
    private String normalizeName(String name) {
        if (name == null) return null;
        String v=name.trim().replace('\\','/'); if(v.startsWith("/imgs/")) v=v.substring(5);
        if(!v.startsWith("/blogs/") || v.contains("..")) return null;
        return v.matches("/blogs/[0-9]+/[0-9]+/[0-9a-fA-F-]+\\.(jpg|jpeg|png|webp)") ? v : null;
    }
}
