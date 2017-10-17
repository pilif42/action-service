set schema 'action';

UPDATE action.actionrule
SET daysoffset = 45
WHERE actionrulepk = 2;

UPDATE action.actionrule
SET daysoffset = 73
WHERE actionrulepk = 3;

UPDATE action.actionrule
SET daysoffset = 45
WHERE actionrulepk = 4;

UPDATE action.actionrule
SET daysoffset = 73
WHERE actionrulepk = 5;

UPDATE action.actionrule
SET daysoffset = 0
WHERE actionrulepk = 1;

-- Create actionrule description and name from the action type
UPDATE action.actionrule ar
SET description =  (SELECT t1.description || '(+' || ar.daysoffset || ' days)' FROM action.actiontype t1 WHERE ar.actiontypeFK = t1.actiontypePK)
   ,name        =  (SELECT t2.name || '+' || ar.daysoffset FROM action.actiontype t2 WHERE ar.actiontypeFK = t2.actiontypePK)
WHERE EXISTS (SELECT 1 FROM action.actiontype t3 WHERE ar.actiontypeFK = t3.actiontypePK);
