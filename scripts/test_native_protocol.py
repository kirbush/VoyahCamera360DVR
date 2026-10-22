import pathlib,sys,subprocess,os
from elf_reader import Elf
from unicorn import Uc,UC_ARCH_ARM64,UC_MODE_ARM
from unicorn.arm64_const import UC_ARM64_REG_X0,UC_ARM64_REG_X30,UC_ARM64_REG_SP
ROOT=pathlib.Path(__file__).resolve().parents[1]
ndk=pathlib.Path(sys.argv[1] if len(sys.argv)>1 else os.environ['AVM_NDK_DIR'])
host='windows-x86_64' if sys.platform=='win32' else 'darwin-x86_64' if sys.platform=='darwin' else 'linux-x86_64'
prebuilt=ndk if (ndk/'bin').is_dir() else ndk/'toolchains/llvm/prebuilt'/host
clang=prebuilt/'bin'/('clang.exe' if sys.platform=='win32' else 'clang')
out=ROOT/'app/build/native-protocol-test';out.parent.mkdir(parents=True,exist_ok=True)
subprocess.run([str(clang),'--target=aarch64-linux-android30','-O2','-nostdlib','-static','-fno-pic','-fno-pie','-Wl,--entry=build_packet','-Wl,-Ttext=0x10000','-o',str(out),str(ROOT/'app/src/main/cpp/protocol_test.c')],check=True)
elf=Elf(out.read_bytes());names={s['name']:s['addr'] for s in elf.symbols}
sections=[s for s in elf.sh if s[2]&2 and s[5]]
base=min(s[3]&~4095 for s in sections);end=max((s[3]+s[5]+4095)&~4095 for s in sections)
uc=Uc(UC_ARCH_ARM64,UC_MODE_ARM);uc.mem_map(base,end-base)
for s in sections:
    if s[1]!=8:uc.mem_write(s[3],elf.blob(s))
uc.mem_map(0x800000,0x2000);uc.mem_map(0x900000,0x1000)
def call(name,value=0):
    uc.reg_write(UC_ARM64_REG_SP,0x801000);uc.reg_write(UC_ARM64_REG_X0,value&0xffffffff);uc.reg_write(UC_ARM64_REG_X30,0x900000)
    uc.emu_start(names[name],0x900000,count=10000);return uc.reg_read(UC_ARM64_REG_X0)
address=call('packet_address')
for view,expected in [(1,bytes([28,8,0,0,0,0,2,0,0,0])),(2,bytes([28,8,0,0,0,0,4,0,0,0]))]:
    assert call('build_packet',view)==10
    assert bytes(uc.mem_read(address,10))==expected
for view in (-1,0,3,255):
    uc.mem_write(address,b'\xaa'*10);assert call('build_packet',view)==0
    assert bytes(uc.mem_read(address,10))==b'\xaa'*10
print('PASS: compiled ARM64 front/rear request encoding and invalid-value rejection')
