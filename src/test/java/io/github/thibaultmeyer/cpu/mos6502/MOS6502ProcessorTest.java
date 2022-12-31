package io.github.thibaultmeyer.cpu.mos6502;

import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Collections;

@TestMethodOrder(MethodOrderer.MethodName.class)
final class MOS6502ProcessorTest {

    @Test
    void test() {

        final MOS6502Processor processor = new MOS6502Processor(Collections.singletonList(new Memory()));
        processor.reset(0x400);

        for (int idx = 0; idx < 1_000; idx += 1) {
            processor.clockTick();
        }
    }

    private static class Memory implements BusUnit {

        private final int[] internalMemory;

        public Memory() {


            try (final InputStream is = this.getClass().getResourceAsStream("/6502_functional_test.bin")) {

                this.internalMemory = new int[is.available()];
                Arrays.fill(this.internalMemory, (byte) 0);

                byte[] r = new byte[is.available()];
                is.read(r, 0, is.available());
                for (int idx = 0; idx < r.length; idx += 1) {
                    this.internalMemory[idx] = (int) (r[idx] & 0xFF);
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public int mappingAddressMin() {

            return 0;
        }

        @Override
        public int mappingAddressMax() {

            return internalMemory.length - 1;
        }

        @Override
        public int read(final int address) {

            return this.internalMemory[address];
        }

        @Override
        public void write(final int address, final int value) {

            this.internalMemory[address] = value;
        }
    }
}
