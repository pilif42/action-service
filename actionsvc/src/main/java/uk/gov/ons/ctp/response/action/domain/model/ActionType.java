package uk.gov.ons.ctp.response.action.domain.model;

import java.io.Serializable;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Domain model object.
 */
@Entity
@Data
@AllArgsConstructor
@Builder
@NoArgsConstructor
@Table(name = "actiontype", schema = "action")
public class ActionType implements Serializable {

  private static final long serialVersionUID = -581549382631976704L;

  @Id
  @Column(name = "actiontypepk")
  private Integer actionTypePK;

  private String name;
  private String description;
  private String handler;

  @Column(name = "cancancel")
  private Boolean canCancel;


  @Column(name = "responserequired")
  private Boolean responseRequired;

}
