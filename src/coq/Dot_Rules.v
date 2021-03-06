(** The DOT calculus -- Rules *)

Require Export Dot_Labels.
Require Import Metatheory.
Require Export Dot_Syntax Dot_Definitions.

(* ********************************************************************** *)
(** * #<a name="red"></a># Reduction *)
Reserved Notation "s |~ a ~~> b  ~| s'" (at level 60).

(* ********************************************************************** *)
(** * #<a name="typing"></a># Typing *)
(* Path Type Assigment *)
Reserved Notation "E |= t ~: T" (at level 69).
(* Membership *)
Reserved Notation "E |= t ~mem~ l ~: D" (at level 69).
(* Expansion *)
Reserved Notation "E |= T ~<! DS" (at level 69).
Reserved Notation "E |= T ~<? DS" (at level 69).
(* Subtyping *)
Reserved Notation "E |= t ~<: T" (at level 69).
(* Declaration subsumption *)
(* E |= D ~<: D' *)
(* Well-formed types *)
(* E |= T ~wf~ *)
(* Well-formed declarations *)
(* E |= D ~wf *)

Inductive expansion_style : Set :=
  | loose : expansion_style
  | precise : expansion_style.

Inductive red : store -> tm -> store -> tm -> Prop :=
  | red_msel : forall s a Tc ags l t v b,
     binds a (Tc, ags) s ->
     lbl.binds l t ags ->
     method_label l ->
     value v ->
     s |~ (msg (ref a) l v b) ~~> (exe a l (t ^^ v) b) ~| s
  | red_msg_tgt1 : forall s s' l e1 e2 e1' t,
     s |~     (path e1) ~~> (path e1')     ~| s' ->
     s |~ (msg e1 l e2 t) ~~> (msg e1' l e2 t) ~| s'
  | red_msg_tgt2 : forall s s' l v1 e2 e2' t,
     value v1 ->
     s |~     (path e2) ~~> (path e2')     ~| s' ->
     s |~ (msg v1 l e2 t) ~~> (msg v1 l e2' t) ~| s'
  | red_sel :forall s a Tc ags l v,
     binds a (Tc, ags) s ->
     lbl.binds l v ags ->
     s |~ (path (sel (ref a) l)) ~~> v ~| s
  | red_sel_tgt : forall s s' l e e',
     s |~         (path e) ~~> (path e')         ~| s' ->
     s |~ (path (sel e l)) ~~> (path (sel e' l)) ~| s'
  | red_new : forall s Tc a ags t,
     lc_tm (new Tc ags t) ->
     concrete Tc ->
     (forall l v, lbl.binds l v (ags ^args^ fvar a) -> (value_label l /\ value_tm v) \/ (method_label l)) ->
     a `notin` dom s ->
     s |~   (new Tc ags t) ~~> t ^^ (ref a)   ~| ((a ~ ((Tc, ags ^args^ (ref a)))) ++ s)
where "s |~ a ~~> b  ~| s'" := (red s a s' b).

Inductive path_res : store -> pt -> loc -> Prop :=
  | path_res_loc : forall s o,
      path_res s (ref o) o
  | path_res_sel : forall s p o li oi Tc ags,
      path_res s p o ->
      binds o (Tc, ags) s ->
      lbl.binds li (path (ref oi)) ags ->
      path_res s (sel p li) oi.

Inductive path_irres : store -> pt -> pt -> Prop :=
  | path_irres_fvar : forall s x,
      path_irres s (fvar x) (fvar x)
  | path_irres_bvar : forall s x,
      path_irres s (bvar x) (bvar x)
  | path_irres_sel : forall s p l,
      path_irres s p p ->
      path_irres s (sel p l) (sel p l).

Inductive type_res : store -> tp -> tp -> Prop :=
  | type_res_tsel : forall s p L o,
      path_res s p o ->
      type_res s (tp_sel p L) (tp_sel (ref o) L).

Inductive type_optres : store -> tp -> tp -> Prop :=
  | type_optres_res : forall s p L o,
      path_res s p o ->
      type_optres s (tp_sel p L) (tp_sel (ref o) L)
  | type_optres_irres : forall s p L,
      path_irres s p p ->
      type_optres s (tp_sel p L) (tp_sel p L).

Inductive typing : env -> pt -> tp -> Prop :=
  | typing_var : forall G P x T,
      lc_tp T ->
      binds x T G ->
      (G, P) |= (fvar x) ~: T
  | typing_ref : forall G P a T args,
      binds a (T, args) P ->
      (G, P) |= (ref a) ~: T
  | typing_sel : forall E t l T',
      value_label l ->
      E |= t ~mem~ l ~: (decl_tm T') ->
      wf_tp E T' ->
      E |= (sel t l) ~: T'
where "E |= t ~: T" := (typing E t T)

with mem : env -> pt -> label -> decl -> Prop :=
  | mem_path : forall E p l T DS D,
      E |= p ~: T ->
      expands loose E T DS ->
      decls_binds l D DS ->
      mem E p l (D ^d^ p)
where "E |= p ~mem~ l ~: D" := (mem E p l D)

with expands : expansion_style -> env -> tp -> decls -> Prop :=
  | expands_loose : forall E T,
      expands loose E T (decls_fin nil)
  | expands_rfn : forall es E T DSP DS DSM,
      expands es E T DSP ->
      and_decls DSP (decls_fin DS) DSM ->
      expands es E (tp_rfn T DS) DSM
  | expands_tsel : forall es E p q L S U DS,
      type_label L ->
      type_optres (snd E) (tp_sel p L) (tp_sel q L) ->
      E |= q ~mem~ L ~: (decl_tp S U) ->
      expands es E U DS ->
      expands es E (tp_sel p L) DS
  | expands_and : forall es E T1 DS1 T2 DS2 DSM,
      expands es E T1 DS1 ->
      expands es E T2 DS2 ->
      and_decls DS1 DS2 DSM ->
      expands es E (tp_and T1 T2) DSM
  | expands_or : forall es E T1 DS1 T2 DS2 DSM,
      expands es E T1 DS1 ->
      expands es E T2 DS2 ->
      or_decls DS1 DS2 DSM ->
      expands es E (tp_or T1 T2) DSM
  | expands_top : forall es E,
      expands es E tp_top (decls_fin nil)
  | expands_bot : forall es E DS,
      bot_decls DS ->
      expands es E tp_bot DS
where "E |= T ~<! DS" := (expands precise E T DS)
  and "E |= T ~<? DS" := (expands loose E T DS)

with sub_tp : env -> tp -> tp -> Prop :=
  | sub_tp_refl : forall E T,
      wf_tp E T ->
      E |= T ~<: T
  | sub_tp_refl_optres : forall E p1 p2 q L,
      wf_tp E (tp_sel p1 L) ->
      wf_tp E (tp_sel p2 L) ->
      type_optres (snd E) (tp_sel p1 L) (tp_sel q L) ->
      type_optres (snd E) (tp_sel p2 L) (tp_sel q L) ->
      E |= (tp_sel p1 L) ~<: (tp_sel p2 L)
  | sub_tp_rfn_r : forall L E S T DS' DS,
      E |= S ~<: T ->
      E |= S ~<? DS' ->
      decls_ok (decls_fin DS) ->
      (forall z, z \notin L -> forall_decls (ctx_bind E z S) (DS' ^ds^ z) ((decls_fin DS) ^ds^ z) sub_decl) ->
      decls_dom_subset (decls_fin DS) DS' ->
      wf_tp E (tp_rfn T DS) ->
      E |= S ~<: (tp_rfn T DS)
  | sub_tp_rfn_l : forall E T T' DS,
      E |= T ~<: T' ->
      decls_ok (decls_fin DS) ->
      wf_tp E (tp_rfn T DS) ->
      E |= (tp_rfn T DS) ~<: T'
  | sub_tp_tsel_r : forall E p L S U S',
      type_label L ->
      E |= p ~mem~ L ~: (decl_tp S U) ->
      E |= S' ~<: S ->
      wf_tp E (tp_sel p L) ->
      E |= S' ~<: (tp_sel p L)
  | sub_tp_tsel_l : forall E p L S U U',
      type_label L ->
      E |= p ~mem~ L ~: (decl_tp S U) ->
      E |= U ~<: U' ->
      wf_tp E (tp_sel p L) ->
      E |= (tp_sel p L) ~<: U'
  | sub_tp_and_r : forall E T T1 T2,
      E |= T ~<: T1 -> E |= T ~<: T2 ->
      E |= T ~<: (tp_and T1 T2)
  | sub_tp_and_l1 : forall E T T1 T2,
      wf_tp E T2 ->
      E |= T1 ~<: T ->
      E |= (tp_and T1 T2) ~<: T
  | sub_tp_and_l2 : forall E T T1 T2,
      wf_tp E T1 ->
      E |= T2 ~<: T ->
      E |= (tp_and T1 T2) ~<: T
  | sub_tp_or_r1 : forall E T T1 T2,
      wf_tp E T2 ->
      E |= T ~<: T1 ->
      E |= T ~<: (tp_or T1 T2)
  | sub_tp_or_r2 : forall E T T1 T2,
      wf_tp E T1 ->
      E |= T ~<: T2 ->
      E |= T ~<: (tp_or T1 T2)
  | sub_tp_or_l : forall E T T1 T2,
      E |= T1 ~<: T -> E |= T2 ~<: T ->
      E |= (tp_or T1 T2) ~<: T
  | sub_tp_top : forall E T,
      wf_tp E T ->
      E |= T ~<: tp_top
  | sub_tp_bot : forall E T,
      wf_tp E T ->
      E |= tp_bot ~<: T
where "E |= S ~<: T" := (sub_tp E S T)

with sub_decl : env -> decl -> decl -> Prop :=
  | sub_decl_tp : forall E S1 T1 S2 T2,
      E |= S2 ~<: S1 ->
      E |= T1 ~<: T2 ->
      sub_decl E (decl_tp S1 T1) (decl_tp S2 T2)
  | sub_decl_tm : forall E T1 T2,
      E |= T1 ~<: T2 ->
      sub_decl E (decl_tm T1) (decl_tm T2)

with wf_tp : env -> tp -> Prop :=
  | wf_rfn : forall L E T DS,
      decls_ok (decls_fin DS) ->
      wf_tp E T ->
      (forall z, z \notin L ->
        forall l d, decls_binds l d (decls_fin DS) -> (wf_decl (ctx_bind E z (tp_rfn T DS)) (d ^d^ z))) ->
      wf_tp E (tp_rfn T DS)
  | wf_tsel : forall E p L S U,
      type_label L ->
      E |= p ~mem~ L ~: (decl_tp S U) ->
      wf_tp E (tp_sel p L)
  | wf_and : forall E T1 T2,
      wf_tp E T1 ->
      wf_tp E T2 ->
      wf_tp E (tp_and T1 T2)
  | wf_or : forall E T1 T2,
      wf_tp E T1 ->
      wf_tp E T2 ->
      wf_tp E (tp_or T1 T2)
  | wf_bot : forall E,
      wf_tp E tp_bot
  | wf_top : forall E,
      wf_tp E tp_top

with wf_decl : env -> decl -> Prop :=
  | wf_decl_tp : forall E S U,
      wf_tp E S ->
      wf_tp E U ->
      wf_decl E (decl_tp S U)
  | wf_decl_tm : forall E T,
      wf_tp E T ->
      wf_decl E (decl_tm T)
.

(* ********************************************************************** *)
(** * #<a name="auto"></a># Automation *)

Scheme typing_indm         := Induction for typing Sort Prop
  with mem_indm            := Induction for mem Sort Prop
  with expands_indm        := Induction for expands Sort Prop
  with sub_tp_indm         := Induction for sub_tp Sort Prop
  with sub_decl_indm       := Induction for sub_decl Sort Prop
  with wf_tp_indm          := Induction for wf_tp Sort Prop
  with wf_decl_indm        := Induction for wf_decl Sort Prop
.
Combined Scheme typing_mutind from typing_indm, mem_indm, expands_indm, sub_tp_indm, sub_decl_indm, wf_tp_indm, wf_decl_indm.

Require Import LibTactics_sf.
Ltac mutind_typing P1_ P2_ P3_ P4_ P5_ P6_ P7_ :=
  cut ((forall E t T (H: E |= t ~: T), (P1_ E t T H)) /\
  (forall E t l d (H: E |= t ~mem~ l ~: d), (P2_ E t l d H)) /\
  (forall es E T DS (H: expands es E T DS), (P3_ es E T DS H)) /\
  (forall E T T' (H: E |= T ~<: T'), (P4_  E T T' H))  /\
  (forall (e : env) (d d' : decl) (H : sub_decl e d d'), (P5_ e d d' H)) /\
  (forall (e : env) (t : tp) (H : wf_tp e t), (P6_ e t H)) /\
  (forall (e : env) (d : decl) (H : wf_decl e d), (P7_ e d H))); [tauto |
    apply (typing_mutind P1_ P2_ P3_ P4_ P5_ P6_ P7_); try unfold P1_, P2_, P3_, P4_, P5_, P6_, P7_ in *; try clear P1_ P2_ P3_ P4_ P5_ P6_ P7_; [  (* only try unfolding and clearing in case the PN_ aren't just identifiers *)
      Case "typing_var" | Case "typing_ref" | Case "typing_sel" | Case "mem_path" | Case "expands_loose" | Case "expands_rfn" | Case "expands_tsel" | Case "expands_and" | Case "expands_or" | Case "expands_top" | Case "expands_bot" | Case "sub_tp_refl" | Case "sub_tp_refl_optres" | Case "sub_tp_rfn_r" | Case "sub_tp_rfn_l" | Case "sub_tp_tsel_r" | Case "sub_tp_tsel_l" | Case "sub_tp_and_r" | Case "sub_tp_and_l1" | Case "sub_tp_and_l2" | Case "sub_tp_or_r1" | Case "sub_tp_or_r2" | Case "sub_tp_or_l" | Case "sub_tp_top" | Case "sub_tp_bot" | Case "sub_decl_tp" | Case "sub_decl_tm" | Case "wf_rfn" | Case "wf_tsel" | Case "wf_and" | Case "wf_or" | Case "wf_bot" | Case "wf_top" | Case "wf_decl_tp" | Case "wf_decl_tm" ];
      introv; eauto ].

Section TestMutInd.
(* mostly reusable boilerplate for the mutual induction: *)
  Let Ptyp (E: env) (t: pt) (T: tp) (H: E |=  t ~: T) := True.
  Let Pmem (E: env) (t: pt) (l: label) (d: decl) (H: E |= t ~mem~ l ~: d) := True.
  Let Pexp (es: expansion_style) (E: env) (T: tp) (DS : decls) (H: expands es E T DS) := True.
  Let Psub (E: env) (T T': tp) (H: E |= T ~<: T') := True.
  Let Psbd (E: env) (d d': decl) (H: sub_decl E d d') := True.
  Let Pwft (E: env) (t: tp) (H: wf_tp E t) := True.
  Let Pwfd (E: env) (d: decl) (H: wf_decl E d) := True.
Lemma EnsureMutindTypingTacticIsUpToDate : True.
Proof. mutind_typing Ptyp Pmem Pexp Psub Psbd Pwft Pwfd; intros; auto. Qed.
End TestMutInd.
