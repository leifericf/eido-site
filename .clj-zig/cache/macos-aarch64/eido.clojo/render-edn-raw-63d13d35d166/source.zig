const std = @import("std");

fn clj_zig_eido_2e_clojo_render_2d_edn_2d_raw__impl(graphEdn_ptr: [*]const u8, graphEdn_len: usize, baseDir_ptr: [*]const u8, baseDir_len: usize) []u8 {
    const graphEdn = graphEdn_ptr[0..graphEdn_len];
    const baseDir = baseDir_ptr[0..baseDir_len];
    const clojo = @import("clojo");
    const alloc = std.heap.c_allocator;
    var threaded = std.Io.Threaded.init(alloc, .{});
    defer threaded.deinit();
    const cio = threaded.io();
    var r = clojo.render(alloc, cio, graphEdn, baseDir);
    defer r.deinit();
    const media = r.media_type;
    const diag = r.diagnostics_text;
    const payload = r.bytes;
    const out = alloc.alloc(u8, 17 + media.len + diag.len + payload.len) catch @panic("oom");
    out[0] = @intFromEnum(r.status);
    std.mem.writeInt(u32, out[1..][0..4], r.width, .little);
    std.mem.writeInt(u32, out[5..][0..4], r.height, .little);
    std.mem.writeInt(u32, out[9..][0..4], @intCast(media.len), .little);
    std.mem.writeInt(u32, out[13..][0..4], @intCast(diag.len), .little);
    var o: usize = 17;
    @memcpy(out[o..][0..media.len], media);
    o += media.len;
    @memcpy(out[o..][0..diag.len], diag);
    o += diag.len;
    @memcpy(out[o..][0..payload.len], payload);
    return out;
}

export fn clj_zig_eido_2e_clojo_render_2d_edn_2d_raw(graphEdn_ptr: [*]const u8, graphEdn_len: usize, baseDir_ptr: [*]const u8, baseDir_len: usize, __ptr: *usize, __len: *usize) void {
    const __r = clj_zig_eido_2e_clojo_render_2d_edn_2d_raw__impl(graphEdn_ptr, graphEdn_len, baseDir_ptr, baseDir_len);
    __ptr.* = @intFromPtr(__r.ptr);
    __len.* = __r.len;
}

export fn clj_zig_eido_2e_clojo_render_2d_edn_2d_raw__free(__ptr: usize, __len: usize) void {
    const __p: [*]u8 = @ptrFromInt(__ptr);
    std.heap.c_allocator.free(__p[0..__len]);
}
