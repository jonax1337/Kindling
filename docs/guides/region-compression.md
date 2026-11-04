# Use LZ4 region files

Every chunk a player had loaded is written to disk again when it unloads, even when nothing in it changed. With vanilla's `deflate`, compressing those chunks is about half of the chunk workers' time while players travel through land that already exists. LZ4 compresses much faster and stores each chunk in about twice the space.

## When it pays off

- Players travel a lot through explored land, and the CPU is the tight resource: a VPS with few cores, or a host where the doctor reports CPU waits.
- The disk has room for the world to grow. As chunks are saved again they double in size, so plan for about twice the current region folders over time.

Keep `deflate` on a small disk, or when backups are stored or uploaded where size costs money.

## Switch to LZ4

1. Stop the server.
2. In `server.properties`, set:

   ```properties
   region-file-compression=lz4
   ```

3. Start the server.

Nothing has to be converted. Chunks saved before stay readable and are written with LZ4 the next time they are saved. Set it before a [pregeneration](pregenerate.md) to write the new chunks with LZ4 right away.

## Switch back

Set `region-file-compression=deflate` and restart. LZ4 chunks stay readable and become `deflate` again when they are saved next. Vanilla and other Paper-based servers read both.

## Check the result

Run `/kindling doctor`. It lists the compression the server uses, and suggests LZ4 when the region files use `deflate` and the disk has room.

The measurement behind these numbers: [Region file compression](../measurements/chunks.md#region-file-compression).
