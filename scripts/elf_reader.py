"""Minimal little-endian ELF reader for the ARM64 protocol unit test."""
import struct
class Elf:
    def __init__(self,data):
        self.data=data
        assert data[:4]==b'\x7fELF' and data[4]==2 and data[5]==1
        h=struct.unpack_from('<HHIQQQIHHHHHH',data,16)
        offset,entry_size,count,name_index=h[5],h[10],h[11],h[12]
        self.sh=[struct.unpack_from('<IIQQQQIIQQ',data,offset+i*entry_size) for i in range(count)]
        self.symbols=[]
        for section in self.sh:
            if section[1] not in (2,11):continue
            strings=self.blob(self.sh[section[6]])
            for start in range(section[4],section[4]+section[5],section[9]):
                name,info,other,index,address,size=struct.unpack_from('<IBBHQQ',data,start)
                self.symbols.append({'name':strings[name:strings.find(b'\0',name)].decode(),'addr':address,'size':size})
    def blob(self,section):return self.data[section[4]:section[4]+section[5]]
