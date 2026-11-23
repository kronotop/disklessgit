/*
 * Copyright (c) 2026 Burak Sezer
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.kronotop.disklessgit.client;

import org.bson.BsonBinaryWriter;
import org.bson.BsonDocument;
import org.bson.codecs.BsonDocumentCodec;
import org.bson.codecs.Codec;
import org.bson.codecs.EncoderContext;
import org.bson.io.BasicOutputBuffer;

public final class BSONUtil {
    private static final EncoderContext ENCODER_CONTEXT = EncoderContext.builder().isEncodingCollectibleDocument(true).build();
    private static final Codec<BsonDocument> BSON_DOCUMENT_CODEC = new BsonDocumentCodec();

    private BSONUtil() {
    }

    public static byte[] toBytes(BsonDocument document) {
        try (BasicOutputBuffer buffer = new BasicOutputBuffer()) {
            BsonBinaryWriter writer = new BsonBinaryWriter(buffer);
            BSON_DOCUMENT_CODEC.encode(writer, document, ENCODER_CONTEXT);
            return buffer.toByteArray();
        }
    }
}
