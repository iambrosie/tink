// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
//
////////////////////////////////////////////////////////////////////////////////

package com.google.crypto.tink.jwt;

import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.crypto.tink.subtle.Base64;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.internal.Streams;
import com.google.gson.stream.JsonReader;
import java.io.StringReader;
import java.security.InvalidAlgorithmParameterException;
import java.util.Locale;

final class JwtFormat {

  private JwtFormat() {}

  private static String validateAlgorithm(String algo) throws InvalidAlgorithmParameterException {
    switch (algo) {
      case "HS256":
      case "HS384":
      case "HS512":
      case "ES256":
      case "ES384":
      case "ES512":
      case "RS256":
      case "RS384":
      case "RS512":
      case "PS256":
      case "PS384":
      case "PS512":
        return algo;
      default:
        throw new InvalidAlgorithmParameterException("invalid algorithm: " + algo);
    }
  }

  static String createHeader(String algorithm) throws InvalidAlgorithmParameterException {
    validateAlgorithm(algorithm);
    JsonObject header = new JsonObject();
    header.addProperty(JwtNames.HEADER_ALGORITHM, algorithm);
    return Base64.urlSafeEncode(header.toString().getBytes(UTF_8));
  }

  static void validateHeader(String expectedAlgorithm, JsonObject header)
      throws InvalidAlgorithmParameterException, JwtInvalidException {
    validateAlgorithm(expectedAlgorithm);

    if (!header.has(JwtNames.HEADER_ALGORITHM)) {
      throw new JwtInvalidException("missing algorithm in header");
    }
    for (String name : header.keySet()) {
      if (name.equals(JwtNames.HEADER_ALGORITHM)) {
        String algorithm = getStringHeader(header, JwtNames.HEADER_ALGORITHM);
        if (!algorithm.equals(expectedAlgorithm)) {
          throw new InvalidAlgorithmParameterException(
              String.format(
                  "invalid algorithm; expected %s, got %s", expectedAlgorithm, algorithm));
        }
      } else if (name.equals(JwtNames.HEADER_TYPE)) {
        String headerType = getStringHeader(header, JwtNames.HEADER_TYPE);
        if (!headerType.toUpperCase(Locale.ROOT).equals(JwtNames.HEADER_TYPE_VALUE)) {
          throw new JwtInvalidException(
              String.format(
                  "invalid header type; expected %s, got %s",
                  JwtNames.HEADER_TYPE_VALUE, headerType));
        }
      } else {
        throw new JwtInvalidException(
            String.format("invalid JWT header: unexpected header %s", name));
      }
    }
  }

  private static String getStringHeader(JsonObject header, String name) throws JwtInvalidException {
    if (!header.has(name)) {
      throw new JwtInvalidException("header " + name + " does not exist");
    }
    if (!header.get(name).isJsonPrimitive() || !header.get(name).getAsJsonPrimitive().isString()) {
      throw new JwtInvalidException("header " + name + " is not a string");
    }
    return header.get(name).getAsString();
  }

  static JsonObject decodeHeader(String headerStr) throws JwtInvalidException {
    try {
      String jsonHeader = new String(Base64.urlSafeDecode(headerStr), UTF_8);
      JsonReader jsonReader = new JsonReader(new StringReader(jsonHeader));
      jsonReader.setLenient(false);
      return Streams.parse(jsonReader).getAsJsonObject();
    } catch (JsonParseException | IllegalArgumentException ex) {
      throw new JwtInvalidException("invalid JWT header: " + ex);
    }
  }

  static String encodePayload(String jsonPayload) {
    return Base64.urlSafeEncode(jsonPayload.getBytes(UTF_8));
  }

  static String decodePayload(String payloadStr) {
    return new String(Base64.urlSafeDecode(payloadStr), UTF_8);
  }

  static String encodeSignature(byte[] signature) {
    return Base64.urlSafeEncode(signature);
  }

  static byte[] decodeSignature(String signatureStr) throws JwtInvalidException {
    try {
      return Base64.urlSafeDecode(signatureStr);
    } catch (IllegalArgumentException ex) {
      throw new JwtInvalidException("invalid JWT signature: " + ex);
    }
  }

  static String createUnsignedCompact(String algorithm, String jsonPayload)
      throws InvalidAlgorithmParameterException {
    return createHeader(algorithm) + "." + encodePayload(jsonPayload);
  }

  static String createSignedCompact(String unsignedCompact, byte[] signature) {
    return unsignedCompact + "." + encodeSignature(signature);
  }

  static void validateASCII(String data) throws JwtInvalidException {
    for (char c : data.toCharArray()) {
      if ((c & 0x80) > 0) {
        throw new JwtInvalidException("Non ascii character");
      }
    }
  }
}
