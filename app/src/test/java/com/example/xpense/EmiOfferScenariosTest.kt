package com.example.xpense

import com.example.xpense.data.entity.Category
import com.example.xpense.sms.SmsParser
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

/**
 * EMI-conversion offers and everything around them. [SmsParser.parseTransaction] is the single
 * gate between an SMS and a saved expense (and therefore Insights): non-null = recorded.
 *
 *  SKIP   — offers, conversion confirmations, promos: the purchase was already recorded from its
 *           own spend SMS, so recording these would double-count.
 *  RECORD — genuine spends, even when they carry an EMI promo footer or an EMI-ish merchant name.
 *
 * Every SMS is "received" on 1 Nov 2026 so the future-date reminder check is active.
 */
class EmiOfferScenariosTest {
    private val cats = listOf(Category(id = 5, name = "Others", iconName = "Category"))
    private val nov1 = LocalDate.of(2026, 11, 1).atStartOfDay(ZoneId.systemDefault())
        .plusHours(10).toInstant().toEpochMilli()

    // knownGap = the parser is known to get this wrong today and fixing it was deliberately
    // deferred (broader wording rules risk skipping real spends). Reported, not asserted.
    private data class Case(val id: String, val record: Boolean, val sms: String, val knownGap: Boolean = false)
    private fun Case.gap() = copy(knownGap = true)
    private fun skip(id: String, sms: String) = Case(id, false, sms)
    private fun rec(id: String, sms: String) = Case(id, true, sms)

    private val exact = "Convert your transaction of INR 17332 into easy monthly EMIs with tenure up to 48 months " +
        "with FLEXI EMI on your Axis Bank Credit Card. Convert now. *T&Cs."

    // A — the reported SMS and formatting variants of it
    private val groupA = listOf(
        skip("A1 exact reported SMS", exact),
        skip("A2 all upper case", exact.uppercase()),
        skip("A3 all lower case", exact.lowercase()),
        skip("A4 split over lines", exact.replace(" with ", "\nwith ").replace(". ", ".\n")),
        skip("A5 extra spaces", exact.replace(" ", "   ")),
        skip("A6 rupee symbol", exact.replace("INR 17332", "₹17,332")),
        skip("A7 Rs. with decimals", exact.replace("INR 17332", "Rs.17,332.00")),
        skip("A8 no CTA / no T&C", "Convert your transaction of INR 17332 into easy monthly EMIs."),
        skip("A9 only FLEXI EMI marker", "Your transaction of INR 17332 is eligible for FLEXI EMI on your Axis Bank Credit Card."),
        skip("A10 truncated preview", "Convert your transaction of INR 17332 into easy monthly EMIs with tenure up to 48 mo"),
        skip("A11 with link", exact + " https://axisbk.com/flexiemi"),
        skip("A12 card digits", "Convert your transaction of INR 17332 on Axis Bank Credit Card XX1234 into EMIs. T&C"),
        skip("A13 'T & C apply'", "Your transaction of INR 17332 can be paid in EMIs. T & C apply."),
        skip("A14 'converted to EMI'", "Txn of INR 17332 on your Axis Bank Credit Card XX1234 can be converted to EMI. Reply EMI."),
        skip("A15 lakh separators", exact.replace("INR 17332", "INR 1,73,320")),
        skip("A16 no space after INR", "Convert your txn of INR17332 into EMI. T&C"),
        skip("A17 'EMI's' apostrophe", "Convert your transaction of Rs 17,332 into easy EMI's today.")
    )

    // B — other issuers' offer wordings
    private val groupB = listOf(
        skip("B1 HDFC SmartEMI + T&C", "Dear Customer, convert your Rs 24,999 purchase at AMAZON on HDFC Bank Credit Card XX1234 into SmartEMI. Reply SEMI to 5676712. T&C"),
        skip("B2 HDFC SmartEMI, no T&C", "Dear Customer, convert your Rs 24,999 purchase at AMAZON on HDFC Bank Credit Card XX1234 into SmartEMI. Reply SEMI to 5676712."),
        skip("B3 ICICI No Cost EMI", "Good news! Convert your recent txn of Rs.15,000 to No Cost EMI on your ICICI Bank Credit Card. SMS EMI to 5676766."),
        skip("B4 SBI FlexiPay", "Convert your SBI Card transaction of Rs.12,500 at FLIPKART into easy EMIs using FlexiPay. SMS FP to 5676791."),
        skip("B5 Kotak split into EMIs", "Pay in easy EMIs! Your recent spend of Rs 8,999 on Kotak Credit Card can be split into 3/6/9 EMIs."),
        skip("B6 Amex instalments (no 'EMI')", "Plan It: Turn your purchase of Rs 30,000 into monthly instalments. Visit amex.co.in").gap(),
        skip("B7 IDFC eligible for EMI", "Your transaction of Rs.5,000 is eligible to be converted into EMI at 0% interest."),
        skip("B8 RBL 'avail EMI on txn'", "Avail EMI on your transaction of Rs 15000 at 1.25% p.m. Call 022-62327777."),
        skip("B9 Hinglish", "Apne transaction Rs 17332 ko aasaan EMI me badlein. T&C"),
        skip("B10 pre-approved loan on card", "Pre-approved Loan of Rs 1,50,000 on your Axis Bank Credit Card. Get it in 2 mins. T&C"),
        skip("B11 balance transfer on EMI", "Transfer outstanding of Rs 50,000 from other cards to your HDFC card on EMI. T&C apply."),
        skip("B12 HDFC eligible txn", "Your transaction of Rs 24,999 at AMAZON on HDFC Bank Credit Card XX1234 is eligible for EMI. Convert it into EMI at 13% p.a. T&C apply.")
    )

    // C — EMI conversion lifecycle on an already-recorded purchase
    private val groupC = listOf(
        skip("C1 converted into 12 EMIs", "Your transaction of Rs 17,332 on Axis Bank Credit Card XX1234 has been converted into 12 EMIs of Rs 1,532."),
        skip("C2 conversion request received", "EMI conversion request for transaction of INR 17332 received. It will be processed in 3 working days."),
        skip("C3 Flexi EMI booking successful", "Your Flexi EMI booking for Rs 17,332 is successful. First EMI of Rs 1,532 will be billed in your next statement."),
        skip("C4 EMI cancelled, billed in full", "Your EMI on txn of Rs 17332 has been cancelled; the amount will be billed in full."),
        skip("C5 EMI loan foreclosed", "Your EMI loan for Rs 17,332 has been foreclosed.")
    )

    // D — genuine spends that must still be recorded
    private val groupD = listOf(
        rec("D1 Axis spent + EMI footer", "Spent INR 25,000 on Axis Bank Card XX1234 at CROMA on 01-11-26. Convert this into easy EMIs, SMS EMI to 5676782. T&C apply."),
        rec("D2 HDFC 'Txn' alert + convert footer", "Txn Rs.24,999.00 On HDFC Bank Card 1234 At AMAZON On 2026-11-01. Convert to EMI: SMS EMI 1234 to 5676712"),
        rec("D3 spent + Avl Limit + footer", "INR 17,332 spent on Axis Bank Card XX1234 at RELIANCE DIGITAL on 01-11-26. Avl Limit: INR 82,668. Convert to easy EMIs: axis.bank/emi"),
        rec("D4 ICICI debited + No Cost EMI", "Rs 15,000 debited from ICICI Card XX1234 at CROMA. Convert to No Cost EMI on iMobile. T&C"),
        rec("D5 SBI 'thank you for using'", "Thank you for using your SBI Card ending 1234 for Rs 12,500 at FLIPKART on 01/11/26. Convert into FlexiPay EMI. T&C").gap(),
        rec("D6 Axis multi-line + footer", "Spent\nCard no. XX1234\nINR 17332\n01-11-26 10:15:00\nRELIANCE DIGI\nAvl Limit: INR 82668\nSMS BLOCK 1234 to 919951860002, if not you. Convert to EMI? T&C"),
        rec("D7 merchant FLEXI GYM", "Rs 2,499 paid at FLEXI GYM via UPI from A/c XX1234."),
        rec("D8 merchant EASY EMI MOBILES (debited)", "Rs 9,999 debited from A/c XX1234 to EASY EMI MOBILES on 01-11-26. UPI Ref 123456"),
        rec("D9 merchant EASY EMI MOBILES (Txn)", "Txn Rs.9,999.00 On HDFC Bank Card 1234 At EASY EMI MOBILES On 2026-11-01"),
        rec("D10 merchant T&C SALON (Txn)", "Txn Rs.500.00 On HDFC Bank Card 1234 At T&C SALON On 2026-11-01"),
        rec("D11 EMI processing fee charged", "Processing fee of Rs 199 + GST charged on your Axis Bank Credit Card for Flexi EMI conversion."),
        rec("D12 Flexi EMI instalment debited", "Your Flexi EMI of Rs 1,532 has been debited from Axis Bank Credit Card XX1234."),
        rec("D13 spent + 'Convert now'", "Rs 17,332 spent at AMAZON on Axis Bank Card XX1234. Convert now into easy EMIs!"),
        rec("D14 loan EMI processed", "Your EMI of Rs 3,250 for loan 778899 has been successfully processed."),
        rec("D15 BoB 'Dr. from A/C' + footer", "Rs.17332.00 Dr. from A/C XXXX1234 and Cr. to amazon@apl. Convert to EMI, T&C")
    )

    // E — offers whose text happens to contain a past-tense money word
    private val groupE = listOf(
        skip("E1 offer says 'you spent'", "Convert the amount you spent at AMAZON (Rs 17,332) into easy EMIs. T&C").gap(),
        skip("E2 offer says 'Already paid'", "Already paid for your new phone? Convert your transaction of Rs 17,332 into EMI.").gap(),
        skip("E3 offer says 'spends'", "Convert your spends of Rs 17,332 into EMIs."),
        skip("E4 offer says 'purchases'", "Convert purchases above Rs 2,500 into EMI on your Axis Bank Credit Card.")
    )

    // F — other EMI-related SMS that must keep their existing behaviour
    private val groupF = listOf(
        skip("F1 EMI due reminder", "Your EMI of Rs 1,532 is due on 05-11-26. Please maintain sufficient balance."),
        skip("F2 EMI bounce", "Your EMI of Rs 1,532 could not be processed due to insufficient funds."),
        skip("F3 EMI future debit", "Your Flexi EMI of Rs 1,532 will be debited on 05-11-2026."),
        skip("F4 OTP for EMI conversion", "123456 is the OTP to convert your transaction of Rs 17332 into EMI. Do not share."),
        skip("F5 EMI reversal credit", "Rs 17,332 credited to your card as EMI conversion reversal.")
    )

    // G — robustness / smallest edges
    private val groupG = listOf(
        skip("G1 empty SMS", ""),
        skip("G2 just 'T&C'", "T&C"),
        skip("G3 just 'Convert now'", "Convert now"),
        skip("G4 'emi'/'Emi' casing", "convert your Transaction of inr 17332 into Emi."),
        skip("G5 long gap before 'into EMIs'", "Convert your recent Axis Bank Credit Card transaction of INR 17332 made at RELIANCE DIGITAL on 30 Oct into EMIs"),
        skip("G6 'E.M.I.' with dots", "Convert your transaction of Rs 17,332 into E.M.I."),
        skip("G7 offer, no amount", "Convert your transactions into easy EMIs on your Axis Bank Credit Card. T&C"),
        skip("G8 'Rs 0' offer", "Convert your transaction of Rs 0 into EMI.")
    )

    private val groups = listOf(
        "A reported SMS + variants" to groupA,
        "B other issuers' offers" to groupB,
        "C conversion lifecycle" to groupC,
        "D real spends with EMI text" to groupD,
        "E offers with past-tense words" to groupE,
        "F other EMI SMS" to groupF,
        "G robustness" to groupG
    )

    private fun run(name: String, cases: List<Case>) {
        val wrong = cases.filter { (SmsParser.parseTransaction(it.sms, emptyList(), cats, nov1) != null) != it.record }
        val failures = wrong.filterNot { it.knownGap }
        println("GROUP $name: ${cases.size - wrong.size}/${cases.size} passed")
        cases.forEach { c ->
            val status = when {
                c !in wrong -> if (c.knownGap) "PASS (gap now fixed)" else "PASS"
                c.knownGap -> "KNOWN GAP"
                else -> "FAIL"
            }
            println("  $status [${if (c.record) "RECORD" else "SKIP"}] ${c.id}")
        }
        assertTrue(
            "${failures.size} wrong in $name:\n" + failures.joinToString("\n") {
                "${it.id} expected ${if (it.record) "RECORD" else "SKIP"}: ${it.sms}"
            },
            failures.isEmpty()
        )
    }

    @Test fun a_reportedSmsAndVariants() = run(groups[0].first, groups[0].second)
    @Test fun b_otherIssuersOffers() = run(groups[1].first, groups[1].second)
    @Test fun c_conversionLifecycle() = run(groups[2].first, groups[2].second)
    @Test fun d_realSpendsWithEmiText() = run(groups[3].first, groups[3].second)
    @Test fun e_offersWithPastTenseWords() = run(groups[4].first, groups[4].second)
    @Test fun f_otherEmiSms() = run(groups[5].first, groups[5].second)
    @Test fun g_robustness() = run(groups[6].first, groups[6].second)
}
