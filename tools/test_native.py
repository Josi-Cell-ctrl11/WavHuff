"""Compile the bundled MP3 encoder and verify real outputs with ffprobe/ffmpeg."""
import ctypes as c
import json
import math
from pathlib import Path
import subprocess
import tempfile

root = Path(__file__).resolve().parents[1] / 'app/src/main/cpp'
sources = 'VbrTag bitstream encoder fft gain_analysis id3tag lame newmdct presets psymodel quantize quantize_pvt reservoir set_get tables takehiro util vbrquantize version mpglib_interface'.split()
with tempfile.TemporaryDirectory() as tmp:
    tmp = Path(tmp)
    lib = tmp / 'libmp3lame.so'
    subprocess.run(['gcc', '-shared', '-fPIC', '-O2', '-DHAVE_CONFIG_H', '-I'+str(root), '-I'+str(root/'lame/include'), '-I'+str(root/'lame/libmp3lame'), *[str(root/'lame/libmp3lame'/f'{s}.c') for s in sources], '-lm', '-o', str(lib)],check=True)
    lame = c.CDLL(str(lib))
    lame.lame_init.restype = c.c_void_p
    for name in ['lame_set_in_samplerate','lame_set_num_channels','lame_set_brate','lame_set_quality','lame_set_bWriteVbrTag']:
        getattr(lame,name).argtypes = [c.c_void_p,c.c_int]
    lame.lame_set_num_samples.argtypes = [c.c_void_p,c.c_ulong]
    lame.lame_init_params.argtypes = [c.c_void_p]
    lame.lame_close.argtypes = [c.c_void_p]
    lame.lame_encode_buffer.argtypes = [c.c_void_p,c.POINTER(c.c_short),c.POINTER(c.c_short),c.c_int,c.c_void_p,c.c_int]
    lame.lame_encode_buffer_interleaved.argtypes = [c.c_void_p,c.POINTER(c.c_short),c.c_int,c.c_void_p,c.c_int]
    lame.lame_encode_flush.argtypes = [c.c_void_p,c.c_void_p,c.c_int]
    lame.lame_get_lametag_frame.argtypes = [c.c_void_p,c.c_void_p,c.c_size_t]
    lame.lame_get_lametag_frame.restype = c.c_size_t
    for rate in [8000,11025,12000,16000,22050,24000,32000,44100,48000]:
        for channels in [1,2]:
            for bitrate in [96,128,192]:
                h = lame.lame_init(); frames = rate
                for name,value in [('lame_set_in_samplerate',rate),('lame_set_num_channels',channels),('lame_set_brate',bitrate),('lame_set_quality',2),('lame_set_bWriteVbrTag',1),('lame_set_num_samples',frames)]:
                    assert getattr(lame,name)(h,value) == 0
                assert lame.lame_init_params(h) == 0
                samples = (c.c_short*(frames*channels))(*[int(12000*math.sin(2*math.pi*440*(i//channels)/rate)) for i in range(frames*channels)])
                out = c.create_string_buffer(2*frames+7200)
                if channels == 2: n = lame.lame_encode_buffer_interleaved(h,samples,frames,out,len(out))
                else: n = lame.lame_encode_buffer(h,samples,samples,frames,out,len(out))
                assert n >= 0
                encoded = out.raw[:n]
                n = lame.lame_encode_flush(h,out,len(out)); assert n >= 0; encoded += out.raw[:n]
                n = lame.lame_get_lametag_frame(h,out,len(out)); assert 0 < n <= len(encoded)
                encoded = out.raw[:n]+encoded[n:]; lame.lame_close(h)
                path = tmp/f'{rate}-{channels}-{bitrate}.mp3'; path.write_bytes(encoded)
                result = json.loads(subprocess.check_output(['ffprobe','-v','error','-show_streams','-of','json',str(path)]))['streams'][0]
                assert result['codec_name'] == 'mp3' and result['channels'] == channels
                assert abs(float(result['duration'])-1) < 0.3
                subprocess.run(['ffmpeg','-v','error','-i',str(path),'-f','null','-'],check=True)
    print('54 MP3 outputs validated: 9 sample rates × 2 channel modes × 3 bitrates.')
