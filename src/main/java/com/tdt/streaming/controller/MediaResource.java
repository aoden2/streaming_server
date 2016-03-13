package com.tdt.streaming.controller;

import com.tdt.streaming.utils.MediaStreamer;

import javax.inject.Singleton;
import javax.ws.rs.*;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.StreamingOutput;
import java.io.*;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.WritableByteChannel;
import java.util.Date;

@Path("/video")
@Singleton
public class MediaResource {

    final int chunk_size = 1024 * 1024;
    private final File video;

    public MediaResource() {
        // serve media from file system
        String MEDIA_FILE = "/sample.mp4";
        URL url = this.getClass().getResource(MEDIA_FILE);
        video = new File(url.getFile());
    }

    //A simple way to verify if the server supports range headers.
    @HEAD
    @Produces("audio/mp3")
    public Response header() {
        return Response.ok().status(206).header(HttpHeaders.CONTENT_LENGTH, video.length()).build();
    }

    @GET
    @Produces("audio/mp3")
    public Response streamAudio(@HeaderParam("Range") String range) throws Exception {
        return buildStream(video, range);
    }

    private Response buildStream(final File asset, final String range) throws Exception {
        // range not requested : Firefox, Opera, IE do not send range headers
        if (range == null) {
            StreamingOutput streamer = new StreamingOutput() {
                @Override
                public void write(final OutputStream output) throws IOException, WebApplicationException {

                    final FileChannel inputChannel = new FileInputStream(asset).getChannel();
                    final WritableByteChannel outputChannel = Channels.newChannel(output);
                    try {
                        inputChannel.transferTo(0, inputChannel.size(), outputChannel);
                    } finally {
                        // closing the channels
                        inputChannel.close();
                        outputChannel.close();
                    }
                }
            };
            return Response.ok(streamer).status(200).header(HttpHeaders.CONTENT_LENGTH, asset.length()).build();
        }

        String[] ranges = range.split("=")[1].split("-");
        final int from = Integer.parseInt(ranges[0]);
        /**
         * Chunk media if the range upper bound is unspecified. Chrome sends "bytes=0-"
         */
        int to = chunk_size + from;
        if (to >= asset.length()) {
            to = (int) (asset.length() - 1);
        }
        if (ranges.length == 2) {
            to = Integer.parseInt(ranges[1]);
        }

        final String responseRange = String.format("bytes %d-%d/%d", from, to, asset.length());
        final RandomAccessFile raf = new RandomAccessFile(asset, "r");
        raf.seek(from);

        final int len = to - from + 1;
        final MediaStreamer streamer = new MediaStreamer(len, raf);
        Response.ResponseBuilder res = Response.ok(streamer).status(206)
                .header("Accept-Ranges", "bytes")
                .header("Content-Range", responseRange)
                .header(HttpHeaders.CONTENT_LENGTH, streamer.getLenth())
                .header(HttpHeaders.LAST_MODIFIED, new Date(asset.lastModified()));
        return res.build();
    }

}
