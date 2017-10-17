set schema 'action';


CREATE SEQUENCE dlq_actioninstructionseq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    MAXVALUE 999999999999
    CACHE 1;


CREATE TABLE dlq_actioninstruction (
    actioninstructionPK bigint NOT NULL,
    handler character varying(50) NOT NULL,
    message text NOT NULL
);


ALTER TABLE ONLY dlq_actioninstruction
    ADD CONSTRAINT dlq_actioninstruction_pkey PRIMARY KEY (actioninstructionPK);
