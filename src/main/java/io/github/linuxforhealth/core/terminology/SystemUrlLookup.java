/*
 * (C) Copyright IBM Corp. 2020
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.linuxforhealth.core.terminology;

import java.io.IOException;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;
import com.fasterxml.jackson.core.type.TypeReference;
import io.github.linuxforhealth.core.Constants;
import io.github.linuxforhealth.core.ObjectMapperUtil;
import io.github.linuxforhealth.hl7.resource.ResourceReader;

/**
 * Utility class for mapping HL7 codes from table 0396 ( https://www.hl7.org/fhir/v2/0396/index.html
 * ) to respective coding system urls.
 * 
 *
 * @author pbhallam
 */
public class SystemUrlLookup {
  private final Map<String, String> systemUrls;

  private static SystemUrlLookup systemURLLookupInstance;

  private SystemUrlLookup() {
    systemUrls = loadFromFile();

  }

  private static Map<String, String> loadFromFile() {
    TypeReference<Map<String, String>> typeRef = new TypeReference<Map<String, String>>() {};
    try {
      String content =
          ResourceReader.getInstance().getResourceInHl7Folder(Constants.CODING_SYSTEM_MAPPING_PATH);
      return ObjectMapperUtil.getYAMLInstance().readValue(content, typeRef);

    } catch (IOException e) {
      throw new IllegalArgumentException("Cannot read codesystem/CodingSystemMapping.yml", e);
    }
  }


  /**
   * Get the system associated with the value
   * 
   * @param value -String
   * @return String
   * 
   */
  protected static String getSystemUrl(String value) {
    if (systemURLLookupInstance == null) {
      systemURLLookupInstance = new SystemUrlLookup();
    }
    if (value != null) {
      return systemURLLookupInstance.systemUrls.get(StringUtils.upperCase(value));
    } else {
      return null;
    }
  }



  protected static String getSystemV2Url(String value) {
    if (value != null) {
      return Constants.HL7V2_SYSTEM_PREFIX + value;
    } else {
      return null;
    }
  }

  public static void initSystemUrlLookup() {
    if (systemURLLookupInstance == null) {
      systemURLLookupInstance = new SystemUrlLookup();
    }

  }
}
