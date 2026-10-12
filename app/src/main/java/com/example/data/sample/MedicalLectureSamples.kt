package com.example.data.sample

data class MedicalSampleLecture(
    val title: String,
    val subject: String,
    val content: String,
    val description: String
)

object MedicalLectureSamples {
    val samples = listOf(
        MedicalSampleLecture(
            title = "Pathology: Acute Coronary Syndromes & Myocardial Infarction",
            subject = "Cardiology",
            description = "Plaque rupture, transmural vs subendocardial ischemia, coronary anatomy, histopathology timeline, and lethal post-MI complications.",
            content = """
Lecture: Acute Coronary Syndromes & Myocardial Infarction
Course: Pathology of the Cardiovascular System (Year 2)

1. PATHOPHYSIOLOGY OF CORONARY ATHEROSCLEROSIS
Atherosclerosis begins with endothelial injury followed by LDL accumulation, oxidation, and macrophage uptake forming foam cells (fatty streak).
Vulnerable plaques contain a thin fibrous cap, large necrotic lipid core, and abundant matrix metalloproteinases secreted by T-cells and macrophages.
Plaque rupture or erosion exposes subendothelial collagen and tissue factor, triggering platelet adhesion (von Willebrand factor to GpIb), activation (thromboxane A2, ADP binding P2Y12), and aggregation (fibrinogen cross-linking via GpIIb/IIIa).
Occlusion causes downstream transmural ischemia (STEMI) or non-occlusive partial subendocardial ischemia (NSTEMI / Unstable Angina).

2. CORONARY ARTERY TERRITORIES
- Left Anterior Descending (LAD) [45-50% of MIs]: Supplies the anterior wall of the left ventricle, anterior two-thirds of the interventricular septum, and apex. Leads V1-V4.
- Right Coronary Artery (RCA) [30-40% of MIs]: Supplies the inferior wall of LV, right ventricle, and in 85-90% of individuals (right-dominant circulation), gives off the posterior descending artery (PDA) supplying the posterior one-third of the septum and AV node. Leads II, III, aVF. Susceptible to AV nodal block and hypotension. Nitrates contraindicated in isolated RV infarct due to preload dependence.
- Left Circumflex (LCx) [15-20% of MIs]: Supplies the lateral wall of the left ventricle. Leads I, aVL, V5, V6.

3. HISTOPATHOLOGIC TIMELINE POST-MI
- 0 to 4 hours: No light microscopic changes. Subcellular glycogen depletion, mitochondrial swelling. Reversible if reperfused within 20-30 min. Risk of ventricular arrhythmia / sudden cardiac death.
- 4 to 24 hours: Coagulative necrosis begins. Wavy myofibers, contraction band necrosis if reperfused, early neutrophilic infiltrate. Dark mottling grossly.
- 1 to 3 days: Dense neutrophilic infiltrate, extensive coagulative necrosis with loss of nuclei and striations. Hyperemic border. Risk of fibrinous pericarditis (friction rub).
- 3 to 14 days: Macrophage infiltration, phagocytosis of necrotic myocytes, followed by granulation tissue with new capillary formation and fibroblast proliferation.
  CRITICAL WINDOW (Days 3-7): Myocardium is softest and mechanically weakest. High risk of:
  a) Free wall rupture -> Cardiac tamponade (Beck's triad: hypotension, JVD, muffled heart sounds).
  b) Interventricular septal rupture -> Harsh holosystolic murmur at left lower sternal border, step-up in oxygen saturation from RA to RV.
  c) Papillary muscle rupture (usually posteromedial papillary muscle supplied solely by RCA/PDA) -> Severe acute mitral regurgitation, holosystolic apical murmur radiating to axilla, pulmonary edema.
- 2 weeks to months: Dense collagenous scar (fibrosis) replaces myocardium. Type I collagen deposition. Risk of ventricular aneurysm (persistent ST elevations, mural thrombus), heart failure, and Dressler syndrome (autoimmune pericarditis occurring 2-10 weeks post-MI, treated with NSAIDs/colchicine).

4. CARDIAC BIOMARKERS
- Cardiac Troponin I / T: Levels rise at 2-4 hours, peak at 24-48 hours, and remain elevated for 7-10 days. Highest diagnostic sensitivity and specificity.
- CK-MB (Creatine Kinase-MB): Rises at 4-6 hours, peaks at 18-24 hours, normalizes within 48-72 hours. Key clinical utility: detecting recurrent infarction (re-infarction) within the first week after initial MI.
            """.trimIndent()
        ),
        MedicalSampleLecture(
            title = "Pharmacology: Autonomic Nervous System & Adrenergic Agonists",
            subject = "Pharmacology",
            description = "Alpha & Beta receptor signaling, G-protein cascades, hemodynamic response curves, and catecholamine pharmacology.",
            content = """
Lecture: Adrenergic Pharmacology: Receptors, Second Messengers, and Agonists
Course: Medical Pharmacology & Therapeutics

1. ADRENERGIC RECEPTOR SUBTYPES & SECOND MESSENGERS
- Alpha-1 (α1): Coupled to Gq protein. Activates phospholipase C (PLC) -> cleaves PIP2 into Inositol 1,4,5-trisphosphate (IP3) and Diacylglycerol (DAG). IP3 causes calcium release from sarcoplasmic reticulum; DAG activates Protein Kinase C (PKC).
  Effects: Vascular smooth muscle contraction (vasoconstriction), pupillary dilator muscle contraction (mydriasis), urinary sphincter contraction.
- Alpha-2 (α2): Coupled to Gi protein. Inhibits adenylyl cyclase -> decreases intracellular cAMP.
  Effects: Presynaptic autoreceptor inhibition (decreases norepinephrine release), decreases insulin secretion from pancreatic beta cells, decreases intraocular pressure (brimonidine).
- Beta-1 (β1): Coupled to Gs protein. Stimulates adenylyl cyclase -> increases cAMP -> activates Protein Kinase A (PKA).
  Effects: Heart: increases heart rate (positive chronotropy), contractility (positive inotropy), and conduction velocity (positive dromotropy). Kidney: increases renin release from juxtaglomerular cells.
- Beta-2 (β2): Coupled to Gs protein. Increases cAMP -> PKA phosphorylation and inactivation of myosin light-chain kinase (MLCK), stimulating calcium efflux.
  Effects: Bronchodilation, vascular smooth muscle relaxation (vasodilation in skeletal muscle), stimulates hepatic glycogenolysis and gluconeogenesis, promotes cellular potassium uptake (hypokalemia risk).
- Beta-3 (β3): Coupled to Gs protein. Lipolysis in adipose tissue; relaxes detrusor muscle (mirabegron used for overactive bladder).

2. CATECHOLAMINE COMPARISONS & HEMODYNAMICS
- Epinephrine (Adrenaline): Agonist at α1, α2, β1, β2.
  Low doses: Predominant β2 effects (vasodilation, decreased diastolic BP, decreased systemic vascular resistance) and β1 (increased heart rate and stroke volume). Mean arterial pressure maintained.
  High doses: Predominant α1 vasoconstriction overtakes β2, leading to elevated systolic and diastolic blood pressure.
  First-line treatment for anaphylactic shock (IM route: β2 relieves bronchospasm; α1 prevents vascular collapse; β1 stabilizes cardiac output).
- Norepinephrine (Noradrenaline): Agonist at α1 > α2 > β1; minimal β2 activity.
  Effects: Potent vasoconstriction (increased SVR) and elevated systolic and diastolic BP. Triggers carotid sinus baroreceptor reflex: vagal activation produces reflex bradycardia. First-line vasopressor for septic shock.
- Isoproterenol: Pure β1 and β2 agonist. Markedly decreases peripheral resistance (β2) while powerfully stimulating heart rate and contractility (β1).
- Phenylephrine: Pure α1 agonist. Vasoconstriction -> increased MAP -> reflex bradycardia. Used as nasal decongestant and for septic shock or priapism.
- Dobutamine: Primarily β1 agonist (with mild β2 and α1). Increases cardiac output and contractility with minimal change in heart rate or SVR. Used in cardiogenic shock and cardiac stress testing.
            """.trimIndent()
        ),
        MedicalSampleLecture(
            title = "Immunology: Hypersensitivity Reactions (Types I to IV)",
            subject = "Immunology",
            description = "Mechanisms of hypersensitivity: Gell-Coombs classification, IgE vs cytotoxic vs immune complex vs cell-mediated pathways.",
            content = """
Lecture: Gell-Coombs Hypersensitivity Classifications & Clinical Correlations
Course: Medical Microbiology & Immunology

1. TYPE I HYPERSENSITIVITY (Immediate / Anaphylactic / Atopic)
- Mechanism: Initial exposure leads to antigen presentation to naive CD4+ T cells, which differentiate into Th2 cells under IL-4 influence.
  Th2 cells secrete IL-4 (induces B-cell class switching to IgE), IL-5 (activates and recruits eosinophils), and IL-13 (stimulates goblet cell mucus production).
  IgE binds high-affinity FcεRI receptors on tissue mast cells and circulating basophils (sensitization phase).
  On re-exposure, multivalent allergen cross-links membrane-bound IgE, triggering rapid degranulation:
  - Immediate phase (minutes): Pre-formed histamine release (vasodilation, increased vascular permeability, smooth muscle spasm), proteases, heparin.
  - Late phase (2-8 hours): Newly synthesized lipid mediators via arachidonic acid cascade (leukotrienes C4, D4, E4 causing prolonged bronchoconstriction; prostaglandins D2).
- Clinical examples: Anaphylaxis (peanuts, bee venom), allergic asthma, seasonal allergic rhinitis, atopic dermatitis, acute urticaria.

2. TYPE II HYPERSENSITIVITY (Antibody-Mediated / Cytotoxic)
- Mechanism: Antibodies (IgG or IgM) directed against intrinsic cellular or tissue-specific antigens.
  Tissue damage occurs through three distinct mechanisms:
  a) Complement-dependent cytotoxicity: Classical complement cascade (C5b-9 MAC formation) or opsonization by C3b leading to phagocytosis by splenic macrophages.
  b) Antibody-dependent cellular cytotoxicity (ADCC): NK cells and neutrophils bind IgG Fc portion via CD16 and release perforin/granzymes.
  c) Cellular dysfunction without cell death: Antibodies act as receptor agonists or antagonists.
- Clinical examples:
  - Autoimmune hemolytic anemia (Coombs positive), Immune thrombocytopenia (ITP, anti-GpIIb/IIIa).
  - Goodpasture syndrome: Anti-alpha-3 chain of Type IV collagen in glomerular and alveolar basement membranes (linear immunofluorescence).
  - Pemphigus vulgaris: Anti-desmoglein-3 antibodies (intraepidermal acantholysis, positive Nikolsky sign).
  - Rheumatic fever: Molecular mimicry of group A streptococcal M protein targeting cardiac myosin.
  - Myasthenia gravis: Anti-acetylcholine receptor (antagonist -> muscle weakness with repeated use).
  - Graves disease: Anti-TSH receptor (agonist -> hyperthyroidism).

3. TYPE III HYPERSENSITIVITY (Immune Complex-Mediated)
- Mechanism: Antigen-antibody immune complexes (typically IgG) form in the circulation and deposit in vessel walls, glomeruli, or synovium, especially at sites of high hydrostatic pressure.
  Deposited complexes fix complement -> C5a and C3a recruit neutrophils -> neutrophils release lysosomal enzymes causing fibrinoid necrosis and vasculitis.
- Clinical examples:
  - Systemic Lupus Erythematosus (SLE): Anti-dsDNA immune complexes depositing in renal glomeruli (lumpy-bumpy granular immunofluorescence) and joints.
  - Post-streptococcal glomerulonephritis (PSGN): Subepithelial "humps" on electron microscopy.
  - Polyarteritis nodosa (PAN): Transmural inflammation of medium-sized arteries.
  - Serum sickness: Occurs 1-2 weeks after foreign protein injection (antivenom, monoclonal antibodies); presents with fever, urticaria, arthralgias, proteinuria, and hypocomplementemia (low C3/C4).
  - Arthus reaction: Localized subacute dermal vasculitis after intradermal booster vaccination (e.g. Tdap) in a pre-sensitized individual.

4. TYPE IV HYPERSENSITIVITY (Delayed-Type / Cell-Mediated)
- Mechanism: Mediated by sensitized T lymphocytes without antibody involvement. Takes 48-72 hours to manifest.
  Two major subtypes:
  a) CD4+ T-cell mediated: Th1 cells secrete IFN-gamma, powerfully activating macrophages into epithelioid histiocytes and multinucleated giant cells (granuloma formation). Th17 cells recruit neutrophils.
  b) CD8+ Cytotoxic T-cell mediated: Perforin and granzyme B induce apoptosis of target cells (e.g., epidermal basal cells).
- Clinical examples:
  - Contact dermatitis (Poison ivy urushiol, nickel, rubber chemicals): Hapten binds self-skin proteins.
  - Tuberculin (PPD) skin test: Induration at 48-72 hours.
  - Multiple sclerosis, Type 1 diabetes mellitus (autoimmune destruction of pancreatic beta cells).
  - Graft-versus-host disease (GVHD) and acute/chronic cellular organ allograft rejection.
  - Stevens-Johnson syndrome / Toxic Epidermal Necrolysis: CD8+ T-cell mediated epidermal detachment.
            """.trimIndent()
        ),
        MedicalSampleLecture(
            title = "Neurology: Cerebrovascular Syndromes & Brainstem Strokes",
            subject = "Neurology",
            description = "Vascular territories, MCA/ACA/PCA syndromes, and lateral vs medial brainstem ischemic syndromes.",
            content = """
Lecture: Stroke Syndromes: Cortical and Brainstem Vascular Territories
Course: Clinical Neuroscience & Neurology

1. CEREBRAL ARTERIAL TERRITORIES
- Middle Cerebral Artery (MCA): Most common site of embolic stroke.
  Supplies lateral motor/sensory cortices (face and arm > leg).
  Key deficits: Contralateral hemiparesis and hemisensory loss (face and upper extremity), contralateral homonymous hemianopia.
  Dominant hemisphere (usually left): Aphasia (Broca expressive with superior division, Wernicke receptive with inferior division, or Global aphasia with MCA stem occlusion).
  Non-dominant hemisphere (usually right): Hemispatial neglect, anosognosia, constructional apraxia.
- Anterior Cerebral Artery (ACA): Supplies medial frontal and parietal lobes.
  Key deficits: Contralateral hemiparesis and sensory loss affecting the LOWER EXTREMITY much more severely than face/arm. Urinary incontinence, abulia, executive dysfunction.
- Posterior Cerebral Artery (PCA): Supplies occipital lobe and splenium of corpus callosum.
  Key deficits: Contralateral homonymous hemianopia WITH MACULAR SPARING (collateral supply to occipital pole from MCA branches). Visual agnosia, alexia without agraphia (if splenium involved).

2. BRAINSTEM STROKE SYNDROMES
- Lateral Medullary Syndrome (Wallenberg Syndrome):
  Vessel: Posterior Inferior Cerebellar Artery (PICA) or Vertebral Artery.
  Structures involved & deficits:
  1. Nucleus ambiguus (CN IX, X, XI): Dysphagia, hoarseness, absent gag reflex, uvula deviation.
  2. Vestibular nuclei: Vertigo, nystagmus, nausea/vomiting.
  3. Inferior cerebellar peduncle: Ipsilateral cerebellar ataxia, dysmetria.
  4. Spinal trigeminal nucleus & tract: Loss of pain and temperature sensation on the IPSILATERAL face.
  5. Spinothalamic tract: Loss of pain and temperature sensation on the CONTRALATERAL body. (Crossed sensory deficit is classic!).
  6. Descending sympathetic fibers: Ipsilateral Horner syndrome (ptosis, miosis, anhidrosis).
  Note: NO MOTOR DEFICIT (corticospinal tract is medial and spared).
- Lateral Pontine Syndrome:
  Vessel: Anterior Inferior Cerebellar Artery (AICA).
  Distinguishing hallmark from Wallenberg: "Facial droop means AICA's pooped."
  Facial nucleus involvement produces IPSILATERAL facial paralysis (LMN pattern including forehead), loss of corneal reflex, decreased taste from anterior two-thirds of tongue.
  Also involves vestibular nuclei (vertigo), cochlear nuclei (sensorineural hearing loss, tinnitus), spinothalamic tract, and ipsilateral Horner syndrome.
- Medial Medullary Syndrome:
  Vessel: Anterior Spinal Artery (ASA) or vertebral artery.
  Structures: Corticospinal tract (contralateral paralysis of limbs), medial lemniscus (contralateral loss of vibration/proprioception), hypoglossal nerve (CN XII: ipsilateral tongue deviation toward the side of the lesion).
            """.trimIndent()
        )
    )
}
