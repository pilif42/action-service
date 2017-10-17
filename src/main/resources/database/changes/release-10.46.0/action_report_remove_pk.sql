-- Function: action.generate_action_mi()

-- DROP FUNCTION action.generate_action_mi();

CREATE OR REPLACE FUNCTION action.generate_action_mi()
  RETURNS boolean AS
$BODY$
DECLARE

v_contents      text;
r_dataline      record;
v_rows          integer;

BEGIN
    
    PERFORM action.logmessage(p_messagetext := 'GENERATING ACTION MI REPORT'
                              ,p_jobid := 0
                              ,p_messagelevel := 'INFO'
                              ,p_functionname := 'action.generate_action_mi');  
    
       v_rows     := 0;
       v_contents := '';
       v_contents := 'Action Plan Name,Action Type,Action Plan Start Date,Days Off Set,Handler,Count,State';

-- Action State Report

       FOR r_dataline IN (SELECT  template.id                               AS actionplan
                                , template.plan_description                 AS action_plan_name
                                , template.type_description                 AS action_type
                                , action_case_cnt.actionplanstartdate::date AS action_plan_startdate      
                                , template.daysoffset                       AS daysoffset
                                , template.handler                          AS handler
                                , COALESCE(action_case_cnt.cnt,0) AS cnt
                                , action_case_cnt.actionstate     AS action_state     
                          FROM (SELECT COALESCE(cases.actionplanFK,actions.actionplanFK) AS  actionplan
                                     , cases.actionplanstartdate
                                     , actions.createddatetime
                                     , COALESCE(cases.actionrulePK,actions.actionruleFK) AS actionrule
                                     , COALESCE(cases.actiontypeFK,actions.actiontypeFK) AS actiontype
                                     , actions.stateFK                                   AS actionstate
                                     , COUNT(*) cnt
                                FROM (SELECT  c.actionplanFK
                                            , c.actionplanstartdate::DATE
                                            , c.casePK
                                            , r.actionrulePK
                                            , r.actiontypeFK
                                      FROM  action.case c
                                          , action.actionrule r
                                      WHERE c.actionplanFK = r.actionplanFK) cases
                                      FULL JOIN (SELECT a.actionplanFK
                                                      , a.createddatetime::DATE
                                                      , a.caseFK
                                                      , a.actionruleFK
                                                      , a.actiontypeFK
                                                      , a.stateFK
                                                 FROM action.action a) actions
                                      ON (actions.actionplanFK = cases.actionplanFK 
                                      AND actions.actionruleFK = cases.actionrulePK
                                      AND actions.actiontypeFK = cases.actiontypeFK
                                      AND actions.caseFK       = cases.casePK) 
                              GROUP BY actionplan, actionplanstartdate, actionrule, actiontype, actionstate, createddatetime) action_case_cnt 
                              FULL JOIN (SELECT  p.id
                                               , r.actionplanFK AS actionplan
                                               , r.actionrulePK AS actionrule
                                               , r.actiontypeFK AS actiontype
                                               , p.description  AS plan_description
                                               , t.description  AS type_description
                                               , r.daysoffset  
                                               , t.handler    
                                         FROM   action.actionplan p
                                              , action.actionrule r
                                              , action.actiontype t
                                         WHERE p.actionplanPK = r.actionplanFK 
                                         AND   r.actiontypeFK = t.actiontypePK) template
                                         ON (template.actionplan = action_case_cnt.actionplan 
                                         AND template.actiontype = action_case_cnt.actiontype
                                         AND template.actionrule = action_case_cnt.actionrule)
                                         ORDER BY template.actionplan,template.daysoffset,template.plan_description,action_plan_startdate) LOOP

                           v_contents := v_contents                         || CHR(10) 
                           || r_dataline.action_plan_name                   || ','
                           || r_dataline.action_type                        || ','
                           || COALESCE(r_dataline.action_plan_startdate::text,'') || ','
                           || r_dataline.daysoffset                         || ','
                           || r_dataline.handler                            || ','
                           || r_dataline.cnt                                || ','
                           || COALESCE(r_dataline.action_state,'') ;   
             v_rows := v_rows+1;  
       END LOOP;       

       -- Insert the data into the report table
       INSERT INTO action.report (id, reportPK,reporttypeFK,contents, createddatetime) VALUES(gen_random_uuid(), nextval('action.reportPKseq'), 'ACTIONS', v_contents, CURRENT_TIMESTAMP); 

               
       PERFORM action.logmessage(p_messagetext := 'GENERATING ACTIONS MI REPORT COMPLETED ROWS WRIITEN = ' || v_rows
                                        ,p_jobid := 0
                                        ,p_messagelevel := 'INFO'
                                        ,p_functionname := 'action.generate_action_mi'); 
      
    
       PERFORM action.logmessage(p_messagetext := 'ACTIONS MI REPORT GENERATED'
                                        ,p_jobid := 0
                                        ,p_messagelevel := 'INFO'
                                        ,p_functionname := 'action.generate_action_mi');
      
  RETURN TRUE;

  EXCEPTION
  WHEN OTHERS THEN   
     PERFORM action.logmessage(p_messagetext := 'GENERATE REPORTS EXCEPTION TRIGGERED SQLERRM: ' || SQLERRM || ' SQLSTATE : ' || SQLSTATE
                               ,p_jobid := 0
                               ,p_messagelevel := 'FATAL'
                               ,p_functionname := 'action.generate_action_mi');
                               
  RETURN FALSE;
END;
$BODY$
  LANGUAGE plpgsql VOLATILE SECURITY DEFINER
  COST 100;