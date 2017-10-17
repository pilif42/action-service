package uk.gov.ons.ctp.response.action.scheduled.ingest;

import lombok.Data;
import lombok.Getter;
import net.sourceforge.cobertura.CoverageIgnore;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;

/**
 * Each line in the ingested CSV is initially mapped to this POJO, whose primary
 * purpose is to perform the up front validation of each field. The fields are
 * deliberately all String type - the csv loader will try and convert "" into
 * Integer and throw an InvalidFormatException otherwise, and we want to
 * validate the fields with more finesse using the javax.validation framework
 */
@CoverageIgnore
@Data
@Getter
public class CsvLine {
  private static final int ESTAB_TYPE_MAX_LEN = 6;
  private static final int TOWN_MAX_LEN = 30;
  private static final int LINE2_MAX_LEN = 60;
  private static final int LINE1_MAX_LEN = 60;
  private static final int CAT_MAX_LEN = 20;
  private static final int ORG_MAX_LEN = 60;
  private static final int LOCALITY_MAX_LEN = 35;
  private static final int ACTION_PLAN_MAX_LEN = 100;
  private static final int QUESTION_SET_MAX_LEN = 10;
  private static final String LADCODE_RE = "[EW][0-9]{8}";
  private static final String POSTCODE_RE = "^[A-Z]{1,2}[0-9R][0-9A-Z]? [0-9][ABD-HJLNP-UW-Z]{2}$";
  private static final String IAC_RE = "[bcdfghjklmnpqrstvwxyz23456789]{12}";
  private static final String NON_BLANK_ALPHANUM_RE = "[\\w]+";
  private static final String NON_BLANK_INTEGER_RE = "[+-]?[\\d]+";
  private static final String NON_BLANK_FLOAT_RE = "[+-]?[\\d]+\\.?[\\d]*";
  private static final String ACTION_TYPE_RE = "\\D*";
  private static final String PRIORITY_RE = "[1-5]";
  private static final String ADDRESS_TYPE_RE = "|HH|CE";
  private static final String INSTRUCTION_TYPE_RE = "Request|Cancel";
  private static final String HANDLER_TYPE_RE = "HotelSurvey|HHSurvey|Printer|Field|CensusSupport";

  // TODO BRES - can we incorporate these into PartySvc API somehow - can PartySvc tell us these?
  // either at runtime or with some sort of XSD definition about Party attributes?
  // the following were all lifted from the old ContactDTO ? how do we determine these now with PartySvc?
  public static final String EMAIL_RE = "^$|[_A-Za-z0-9-\\+]+(\\.[_A-Za-z0-9-]+)*@[A-Za-z0-9-]+(\\.[A-Za-z0-9]+)"
          + "*(\\.[A-Za-z]{2,})";
  public static final String TELEPHONE_RE = "[\\d]{0,11}";
  public static final int TITLE_MAX_LEN = 20;
  public static final int FORENAME_MAX_LEN = 35;
  public static final int SURNAME_MAX_LEN = 35;

  @Pattern(regexp = HANDLER_TYPE_RE)
  private String handler;

  @NotNull
  @Size(min = 1, max = ACTION_PLAN_MAX_LEN)
  private String actionPlan;

  @NotNull
  @Size(min = 1, max = QUESTION_SET_MAX_LEN)
  private String questionSet;

  @NotNull
  @Pattern(regexp = ACTION_TYPE_RE)
  private String actionType;

  @Pattern(regexp = INSTRUCTION_TYPE_RE)
  private String instructionType;

  @Pattern(regexp = ADDRESS_TYPE_RE)
  private String addressType;

  @Size(min = 0, max = ESTAB_TYPE_MAX_LEN)
  private String estabType;

  @Size(min = 0, max = LOCALITY_MAX_LEN)
  private String locality;

  @Size(min = 0, max = ORG_MAX_LEN)
  private String organisationName;

  @Size(min = 0, max = CAT_MAX_LEN)
  private String category;

  @Size(min = 0, max = LINE1_MAX_LEN)
  private String line1;

  @Size(min = 0, max = LINE2_MAX_LEN)
  private String line2;

  @Size(min = 0, max = TITLE_MAX_LEN)
  private String title;

  @Size(min = 0, max = FORENAME_MAX_LEN)
  private String forename;

  @Size(min = 0, max = SURNAME_MAX_LEN)
  private String surname;

  @Pattern(regexp = EMAIL_RE)
  private String emailAddress;

  @Pattern(regexp = TELEPHONE_RE)
  private String telephoneNumber;

  @Size(min = 0, max = TOWN_MAX_LEN)
  private String townName;

  @Pattern(regexp = POSTCODE_RE)
  private String postcode;

  @Pattern(regexp = LADCODE_RE)
  private String ladCode;

  @Pattern(regexp = NON_BLANK_FLOAT_RE)
  private String latitude;

  @Pattern(regexp = NON_BLANK_FLOAT_RE)
  private String longitude;

  @Pattern(regexp = NON_BLANK_INTEGER_RE)
  private String uprn;

  // TODO BRES - regex for UUID needed
  //@Pattern(regexp = NON_BLANK_INTEGER_RE)
  private String caseId;

  @Pattern(regexp = NON_BLANK_ALPHANUM_RE)
  private String caseRef;

  @Pattern(regexp = PRIORITY_RE)
  private String priority;

  @Pattern(regexp = IAC_RE)
  private String iac;

  private String events;
}
