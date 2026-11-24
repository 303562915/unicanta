package io.github.samolego.canta.data.imagebed

import kotlin.random.Random

/**
 * 图片放在哪儿、地址怎么拼：一个自建的 Cloudflare ImageBed（就是 `cloudflare-imgbed` 那套的部署）。
 *
 * 为什么不用 Supabase storage 了：那个 bucket 跟着 Supabase 走，读一张图要带上 `apikey`，还得先在
 * 库里存"对象路径"，客户端再拼出 `.../storage/v1/object/chat-images/<路径>`。图床的读是**公开免鉴权**
 * 的直链，消息里可以直接存网址，别的地方（网页版、分享出去的一段文本）也能拿去用。
 *
 * 接口细节照 `cloudflare-imgbed/对接文档.md`（那份仓库只读，别按这里改它）：
 *  * 上传 `POST [UPLOAD_URL]`，`multipart/form-data`，字段 `file`（必须带文件名）、`uid`、`fileName`、
 *    `folder`、`overwrite`。**`folder` 优先**：同时给了 `fileName` 里的目录也只用文件名那一截。
 *    重名默认 409（`overwrite=0`）。
 *  * 成功回 `{"code":0,"id":"...","url":"https://.../file/...","size":...}`；失败是 4xx/5xx + `{"code":1,"msg":"..."}`。
 *  * 读图 `GET [BASE_URL]/file/<id>`，免鉴权。
 *
 * 这里只放**协议常量与拼装**（地址、目录、上限、请求体编码），发请求的那一半在
 * `SocialRepository.uploadChatImageToImageBed`——它已经有 HttpClient 和 JSON 解析器，不必为一个上传
 * 再养一个客户端。
 */
object ImageBed {
    /** 图床的根。 */
    const val BASE_URL = "https://cloudflare-imgbed-erh.pages.dev"

    /** 上传入口；拼在 [BASE_URL] 上。 */
    const val UPLOAD_URL = "$BASE_URL/api/upload"

    /**
     * 上传用的 Token（`uid` 字段）。写死在这里是因为它就是个上传权限的匿名 Token，没有用户身份；
     * 换 Token 只改这一行。
     */
    const val TOKEN = "imgbed_a9ca1aaf9b3ada8ef67725bc04122824f1db3123dc4a5b46434f1fad2c24cb20"

    /** 所有东西都在这个根目录下面，图床后台一眼能认出哪些是本 app 传的。 */
    const val ROOT = "unicanta"

    /**
     * 头像的目录。
     *
     * 传成 `[AVATAR_FOLDER]/<owner>.jpg`（见 [avatarId]），**覆盖式写**：同一个人重传就是换头像，
     * 不生成新名字——设置里存的是这条直链，名字一变那个地址就 404 了。
     * owner 是纯数字，不用编码。
     */
    const val AVATAR_FOLDER = "$ROOT/头像"

    /**
     * 聊天图片的目录；每个会话再分一层（[chatImageId]），这样清一个会话的图不用扫全库。
     */
    const val CHAT_FOLDER = "$ROOT/群聊"

    /**
     * 单张图的上限：2.5 兆（2.5 MiB = 2_621_440 字节）。
     *
     * 图床自己没有这个限制（单请求 100MB），这是**我们自己的产品规则**：手机拍的图动辄 5～10MB，
     * 聊天里没人需要原图，压到 2.5MB 以内既省流量也省图床的空间。发图前超过它就先压
     * （`CantaPlatform.compressImage`），压不下来就不发。
     */
    const val MAX_BYTES = 2_621_440

    /**
     * 头像的体积上限：200 KiB。
     *
     * 头像最后画的是一个 96dp 的圆（3 倍屏也就 288 像素），200KB 已经比"足够清楚"多出好几倍；
     * 再往上只是白花流量和图床空间。裁剪 + 压缩由平台层做（`CantaPlatform.compressAvatar`）。
     */
    const val AVATAR_MAX_BYTES = 204_800

    /**
     * 头像的体积下限：20 KiB。
     *
     * 这不是硬性要求，是一条"别压过头"的线：256×256 的图如果只剩几 KB，圆里就会出色块。
     * 但纯色、插画这类图本来就只有几 KB，不必为了凑体积把质量调上去——所以平台层压到
     * [AVATAR_MAX_BYTES] 以内就收工，不会回头再加质量。
     */
    const val AVATAR_MIN_BYTES = 20_480

    /**
     * 裁剪预览图的上限：长边 ≤2048、≤400 KiB（由 `CantaPlatform.compressImage` 压出来）。
     *
     * 换头像时先把原图缩成这一份再摆裁剪框：原图可能两千万像素，直接解成位图要几十 MB，低端机会闪退；
     * 而裁剪只需要"看得清挪到哪儿了"。**真正下刀的是原图**，预览只用来量比例。
     */
    const val AVATAR_PREVIEW_BYTES = 409_600

    /** [id] 的公开地址。读图不需要任何鉴权。 */
    fun fileUrl(id: String): String = "$BASE_URL/file/" + encodePath(id.trimStart('/'))

    /**
     * 聊天图片的 id：`unicanta/群聊/<会话号>/<时间戳>-<随机数>.jpg`。
     *
     * 带时间戳和随机数是为了让"同一张图再发一次"落到两个对象上：图床是"重名就 409"的，
     * 固定的名字会让第二次发图直接失败，而覆盖又会让上一条消息换图。
     */
    fun chatImageId(conversationId: Long, stamp: Long, salt: Int): String =
        "$CHAT_FOLDER/$conversationId/$stamp-$salt.jpg"

    /**
     * 头像的 id：`unicanta/头像/<owner>.jpg`，**[owner] 一个人一个名字**。
     *
     * 和 [chatImageId] 正好相反：聊天图每发一次要落到新对象上（否则上一条消息会换图），头像则是
     * "重传就是换一张"。名字固定，所以上传时必须带 `overwrite=1`（见
     * `SocialRepository.uploadAvatar`），否则图床会拿 409 把第二次上传挡回去。
     */
    fun avatarId(owner: String): String = "$AVATAR_FOLDER/${owner.trim()}.jpg"

    /** [id] 里目录那一段（没有目录就是空串）。上传时要单独交给 `folder` 字段。 */
    fun folderOf(id: String): String = id.substringBeforeLast('/', "")

    /** [id] 里文件名那一段。 */
    fun fileNameOf(id: String): String = id.substringAfterLast('/')

    /** 一次请求用的 multipart 分隔串。 */
    fun newBoundary(): String = "----Unicanta" + Random.nextInt(Int.MAX_VALUE).toString(16)

    /**
     * 手工拼一个 `multipart/form-data` 请求体，交给 Ktor 当普通 `ByteArray` 发出去。
     *
     * 不用 Ktor 的 `MultiPartFormDataContent` 是有意的：那个东西要靠 `formData {}` 那一组 builder，
     * 拼出来的边界和文件名编码在这里看不见、也没法只靠读代码确认；这份仓库又不能在本地构建，
     * 出问题只能去翻 CI 日志。二十行字节拼接换来的是"字节长什么样，读一遍就知道"。
     *
     * 文件名只用 ASCII（`1699-314.jpg`），目录（可能带中文，如 `unicanta/群聊/12`）走 `folder` 字段——表单
     * 值按 UTF-8 原样写进请求体，不需要 RFC 5987 那套编码，图床那边解出来就是对的。
     *
     * 字段顺序：先普通字段，`file` 放最后（有的服务端读到 file 就开始写盘，前面缺字段就白写了）。
     */
    fun multipartBody(
        boundary: String,
        fields: List<Pair<String, String>>,
        fileName: String,
        contentType: String,
        bytes: ByteArray,
    ): ByteArray {
        val dash = "--"
        val crlf = "\r\n"
        val head = buildString {
            for ((name, value) in fields) {
                append(dash).append(boundary).append(crlf)
                append("Content-Disposition: form-data; name=\"").append(name).append('"').append(crlf)
                append(crlf).append(value).append(crlf)
            }
            append(dash).append(boundary).append(crlf)
            append("Content-Disposition: form-data; name=\"file\"; filename=\"")
                .append(fileName).append('"').append(crlf)
            append("Content-Type: ").append(contentType).append(crlf)
            append(crlf)
        }
        val tail = crlf + dash + boundary + dash + crlf
        val headBytes = head.encodeToByteArray()
        val tailBytes = tail.encodeToByteArray()
        val body = ByteArray(headBytes.size + bytes.size + tailBytes.size)
        headBytes.copyInto(body, 0)
        bytes.copyInto(body, headBytes.size)
        tailBytes.copyInto(body, headBytes.size + bytes.size)
        return body
    }

    /**
     * 路径按 URL 的规矩编码：只留 unreserved（字母、数字、`-._~`）和 `/`，其余按 UTF-8 逐字节写成
     * `%XX`。图床回的 `url` 本来就是编码好的，这一层是它没回 `url` 时的兜底（中文目录不编码就是
     * 一个非法 URL，Ktor 那边可能直接抛）。
     */
    private fun encodePath(path: String): String {
        val hex = "0123456789ABCDEF"
        return buildString(path.length + 16) {
            for (ch in path) {
                if (ch == '/' || ch == '-' || ch == '.' || ch == '_' || ch == '~' ||
                    (ch.code < 128 && ch.isLetterOrDigit())
                ) {
                    append(ch)
                } else {
                    for (byte in ch.toString().encodeToByteArray()) {
                        val value = byte.toInt()
                        append('%').append(hex[(value shr 4) and 0xF]).append(hex[value and 0xF])
                    }
                }
            }
        }
    }
}
