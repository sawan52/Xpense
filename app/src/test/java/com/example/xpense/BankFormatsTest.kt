package com.example.xpense

import com.example.xpense.data.entity.Category
import com.example.xpense.sms.SmsParser
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

/**
 * Bank SMS formats checked for the three things a recorded payment needs: it is recorded, the
 * AMOUNT is right and the MERCHANT name is right ("Unknown" when the SMS names no payee).
 *
 *  - [bankFormats]: one SMS per bank format across HDFC, SBI, ICICI, Axis, Kotak, BOI, BoB, PNB,
 *    Canara, IDFC, Yes, IndusInd, Federal and Paytm, plus name and amount shapes.
 *  - [fallbackScenarios]: variations aimed at the fallback layers (rupee sign, balance-first
 *    amounts, name tails, "*"/"'"/"&" in names, UPI/P2M paths, "towards", "txn to", junk guard).
 *  - [unchangedBehaviour]: formats the original patterns already handled, pinned so the fallbacks
 *    never change them.
 *
 * Names are capped at 25 characters by design, so a 25-character prefix of the expected name passes.
 */
class BankFormatsTest {
    private val cats = listOf(Category(id = 5, name = "Others", iconName = "Category"))
    private val oct17 = LocalDate.of(2026, 10, 17).atStartOfDay(ZoneId.systemDefault()).plusHours(10).toInstant().toEpochMilli()

    private data class C(val id: String, val sms: String, val amount: Double, val merchant: String)

    private val bankFormats = listOf(
        C("H1", "Txn Rs.50.00\nOn HDFC Bank Card 1234\nAt *28ds@pty \nby UPI 999999999999\nOn 17-10\nNot You?\nCall 18002586161/SMS BLOCK CC 1234 to 7308080808", 50.0, "28ds@pty"),
        C("H2", "Txn Rs.212.00\nOn HDFC Bank Card 1234\nAt ZeptoMarketplacePriva.pay\nby UPI 213200001111\nOn 17-10\nNot You?\nCall 18002586161/SMS BLOCK CC 1234 to 7308080808", 212.0, "ZeptoMarketplacePriva.pay"),
        C("H3", "Txn Rs.120.00\nOn HDFC Bank Card 1234\nAt 9876543210@ybl\nby UPI 628900001111\nOn 17-10\nNot You?\nCall 18002586161/SMS BLOCK CC 1234 to 7308080808", 120.0, "9876543210@ybl"),
        C("H4", "Txn Rs.340.00\nOn HDFC Bank Card 1234\nAt paytmqr28100505010abcd@paytm\nby UPI 628900002222\nOn 17-10\nNot You?\nCall 18002586161/SMS BLOCK CC 1234 to 7308080808", 340.0, "paytmqr28100505010abcd@paytm"),
        C("H5", "Txn Rs.99.00\nOn HDFC Bank Card 1234\nAt swiggy.rzp@hdfcbank\nby UPI 628900003333\nOn 17-10\nNot You?\nCall 18002586161/SMS BLOCK CC 1234 to 7308080808", 99.0, "swiggy.rzp@hdfcbank"),
        C("H6", "Spent Rs.1,250.00 From HDFC Bank Card x1234 At ..BIG BAZAAR_ On 2026-10-17:12:30:09 Bal Rs.5,000.00 Not You? Call 18002586161/SMS BLOCK DC 1234 to 7308080808", 1250.0, "BIG BAZAAR"),
        C("H7", "Spent Rs.2,499 On HDFC Bank Card 1234 At PTM*FLIPKART On 2026-10-17:18:02:11 Not You? Call 18002586161/SMS BLOCK CC 1234 to 7308080808", 2499.0, "PTM*FLIPKART"),
        C("H8", "Spent Rs.649 On HDFC Bank Card 1234 At RAZ*SHRI RAM STORES On 2026-10-17:09:15:00 Not You? Call 18002586161", 649.0, "RAZ*SHRI RAM STORES"),
        C("H9", "Sent Rs.500.00\nFrom HDFC Bank A/C *1234\nTo RAHUL KUMAR\nOn 17/10/26\nRef 628912345678\nNot You?\nCall 18002586161/SMS BLOCK UPI to 7308080808", 500.0, "RAHUL KUMAR"),
        C("H10", "Sent Rs.75.00\nFrom HDFC Bank A/C *1234\nTo blinkit.payu@hdfcbank\nOn 17/10/26\nRef 628912345679\nNot You?\nCall 18002586161/SMS BLOCK UPI to 7308080808", 75.0, "blinkit.payu@hdfcbank"),
        C("H11", "Rs.1,000.00 debited from a/c **1234 on 17-10-26 to VPA zomato@hdfcbank (UPI Ref No 628212345678). Not you? Call 18002586161", 1000.0, "zomato@hdfcbank"),
        C("S1", "Dear UPI user A/C X1234 debited by 250.00 on date 17Oct26 trf to ZOMATO LIMITED Refno 628912345678. If not u? call 1800111109. -SBI", 250.0, "ZOMATO LIMITED"),
        C("S2", "Rs.1,499.00 spent on your SBI Credit Card ending 1234 at AMAZON on 17/10/26. Trxn. not done by you? Report at https://sbicard.com/Dispute", 1499.0, "AMAZON"),
        C("S3", "Dear Customer, Rs.2,000 withdrawn at SBI ATM S1NB000123 from A/c X1234 on 17Oct26. Avl Bal Rs 8,000. Not you? Call 1800111109", 2000.0, "SBI ATM S1NB000123"),
        C("I1", "ICICI Bank Acct XX123 debited for Rs 240.00 on 17-Oct-26; UBER INDIA credited. UPI:628912345678. Call 18002662 for dispute. SMS BLOCK 123 to 9215676766.", 240.0, "UBER INDIA"),
        C("I2", "INR 1,234.00 spent using ICICI Bank Card XX1234 on 17-Oct-26 on IND*AMAZON. Avl Limit: INR 50,000.00. If not you, call 1800 2662/SMS BLOCK 1234 to 9215676766.", 1234.0, "IND*AMAZON"),
        C("I3", "Rs 799.00 spent on Amazon Pay ICICI Bank Credit Card XX1234 at MYNTRA on 2026-10-17. Avl Lmt: Rs 2,00,000", 799.0, "MYNTRA"),
        C("A1", "Spent INR 299\nAxis Bank Card no. XX3768\n17-10-26 08:20:36 IST\nGOOGLEPLAY\nAvl Limit: INR 154392.4\nNot you? SMS BLOCK 3768 to 919951860002", 299.0, "GOOGLEPLAY"),
        C("A2", "Spent INR 1,050\nAxis Bank Card no. XX3768\n17-10-26 20:11:02 IST\nPTM*FLIPKAR\nAvl Limit: INR 150000\nNot you? SMS BLOCK 3768 to 919951860002", 1050.0, "PTM*FLIPKAR"),
        C("A3", "INR 150.00 debited\nA/c no. XX1234\n17-10-26, 12:30:45\nUPI/P2M/628912345678/SWIGGY\nNot you? SMS BLOCKUPI Cust ID to 919951860002\nAxis Bank", 150.0, "SWIGGY"),
        C("K1", "Sent Rs.300.00 from Kotak Bank AC X1234 to rahul@okaxis on 17-10-26.UPI Ref 628912345678. Not you, https://kotak.com/KBANKT/Fraud", 300.0, "rahul@okaxis"),
        C("K2", "Rs.1,000.00 debited from Kotak Bank a/c XX1234 to BHARATPE MERCHANT on 17-10-26. UPI Ref 628912345000", 1000.0, "BHARATPE MERCHANT"),
        C("B1", "BOI UPI - Your account has been debited towards Google for 59.00 on 28/09/2026 (UPI Ref no 123456).", 59.0, "Google"),
        C("B2", "BOI - Rs 4000.00 has been Debited (NACH) in your account XXXX1234 - NACH DR INW - GROWW INVEST TEC ON 17-10-2026. Avl Bal 32902.85", 4000.0, "Unknown"),
        C("B3", "Rs.587.00 Dr. from A/C XXXXXX8394 and Cr. to delhiverylimited1.rzp@hdfcbank. Ref:123456. AvlBal:Rs10,000.00(2026:10:17 12:00:00). Not you? Call 18005700-BOB", 587.0, "delhiverylimited1.rzp@hdfcbank"),
        C("P1", "A/c XX1234 debited INR 500.00 Dt 17-10-26 thru UPI:628912345678. Bal INR 10,000.00 Not u? Fwd this SMS to 9264092640 to block UPI.-PNB", 500.0, "Unknown"),
        C("C1", "An amount of INR 100.00 has been DEBITED to your account XXX123 on 17/10/2026 towards UPI/P2M/628912345678/SWIGGY. Total Avail.bal INR 5,000.00. - Canara Bank", 100.0, "SWIGGY"),
        C("ID1", "Your A/C XXXXX1234 has been debited by INR 250.00 on 17-OCT-2026 for UPI txn to SWIGGY. UPI Ref 628912345678. Not you? call 18002700720", 250.0, "SWIGGY"),
        C("Y1", "INR 500.00 debited from A/c XX1234 on 17-10-2026 via UPI to BIGBASKET (Ref 628912345678). Avl Bal: INR 5,000.00 -Yes Bank", 500.0, "BIGBASKET"),
        C("IN1", "Your IndusInd Bank A/C XX1234 is debited for INR 299.00 towards NETFLIX on 17/10/2026. Avl Bal INR 20,000.00", 299.0, "NETFLIX"),
        C("F1", "Rs 450.00 debited from your A/c XX1234 to VPA zepto@axl on 17-10-2026 Ref No 628912345678 - Federal Bank", 450.0, "zepto@axl"),
        C("PT1", "Paid Rs.120 to Chai Point from Paytm Payments Bank a/c XX1234. UPI Ref: 628912345678", 120.0, "Chai Point"),
        C("M1", "Rs.230.00 debited at 1MG TECHNOLOGIES on 17-10-26. Avl Bal Rs 9,000", 230.0, "1MG TECHNOLOGIES"),
        C("M2", "Rs.85.00 spent at 7-ELEVEN on card XX1234 on 17-10-26", 85.0, "7-ELEVEN"),
        C("M3", "Rs.420.00 spent at MCDONALD'S on card XX1234 on 17-10-26", 420.0, "MCDONALD'S"),
        C("M4", "Rs.1,999.00 spent at H&M on card XX1234 on 17-10-26", 1999.0, "H&M"),
        C("M5", "Rs.649.00 spent at AMAZON PAY INDIA PRIVATE LIMITED on card XX1234", 649.0, "AMAZON PAY INDIA PRIVATE LIMITED"),
        C("M6", "Spent Rs.75 On HDFC Bank Card 1234 At _ZOMATO On 2026-10-17:13:00:00", 75.0, "ZOMATO"),
        C("M7", "Txn Rs.60.00\nOn HDFC Bank Card 1234\nAt *ab12@pty\nby UPI 628900004444\nOn 17-10", 60.0, "ab12@pty"),
        C("AM1", "Rs.1,23,456.78 debited from A/c XX1234 to VPA carsales@icici on 17-10-26", 123456.78, "carsales@icici"),
        C("AM2", "INR1,250.00 debited from A/c XX1234 to VPA dmart@ybl on 17-10-26", 1250.0, "dmart@ybl"),
        C("AM3", "Rs 50 paid to chaiwala@ybl via UPI on 17-10-26", 50.0, "chaiwala@ybl via UPI"),
        C("AM4", "₹250.00 debited from A/c XX1234 to swiggy@icici via UPI on 17-10-26", 250.0, "swiggy@icici via UPI"),
        C("AM5", "Your a/c XX1234 (Bal Rs 12,000.00) is debited with Rs 500.00 for UPI to zomato@icici.", 500.0, "zomato@icici"),
        C("AM6", "Amt Rs. 345.00 debited from A/c XX1234 to VPA uber@axis on 17-10-26", 345.0, "uber@axis"),
        C("AM7", "Rs.0.50 debited from A/c XX1234 to VPA test@ybl on 17-10-26", 0.5, "test@ybl")
    )

    private val fallbackScenarios = listOf(
        // Rupee sign (used only when no Rs/INR/Amt amount exists)
        C("N1", "₹ 1,499 spent on card XX1234 at AMAZON on 17-10-26", 1499.0, "AMAZON"),
        C("N2", "Rs 120 debited from A/c XX1234 to zomato@icici (₹120)", 120.0, "zomato@icici"),
        // Balance or limit before the spend
        C("N3", "Avl Bal: INR 9,000.00. INR 250 debited from A/c XX1234 to swiggy@ybl on 17-10-26", 250.0, "swiggy@ybl"),
        C("N4", "Avl Limit INR 50,000. Spent INR 700 at CROMA on 17-10-26", 700.0, "CROMA"),
        C("N5", "A/c XX1234 debited. Avl Bal Rs 5,000", 5000.0, "Unknown"),  // only a balance: old behaviour kept
        // Name tails
        C("N6", "Rs.320 spent at PVR CINEMAS on 17 Oct 2026", 320.0, "PVR CINEMAS"),
        C("N7", "Rs.320 spent at INOX on Oct 17", 320.0, "INOX"),
        C("N8", "Rs 999 debited to VPA netflix@icici on your card XX1234", 999.0, "netflix@icici"),
        // "*", "'" and "&" inside names
        C("N9", "Rs.540 spent at DOMINO'S PIZZA on 17-10-26", 540.0, "DOMINO'S PIZZA"),
        C("N10", "Rs 2,100 spent at M&S on card XX1234", 2100.0, "M&S"),
        C("N11", "Rs 450 spent at BARBEQUE&CO on 17-10-26", 450.0, "BARBEQUE&CO"),
        C("N12", "Spent Rs.300 On HDFC Bank Card 1234 At PAYU*SWIGGY On 2026-10-17:10:00:00", 300.0, "PAYU*SWIGGY"),
        // UPI path, ICICI "on X*Y", "towards", "txn to"
        C("N13", "INR 89.00 debited\nA/c no. XX1234\n17-10-26, 09:10:11\nUPI/P2A/628900009999/RAMESH KUMAR\nNot you? SMS BLOCKUPI Cust ID to 919951860002\nAxis Bank", 89.0, "RAMESH KUMAR"),
        C("N14", "INR 640 spent using ICICI Bank Card XX1234 on 17-Oct-26 on PYU*ZOMATO. Avl Limit: INR 40,000", 640.0, "PYU*ZOMATO"),
        C("N15", "Your A/C XX1234 is debited for INR 199.00 towards SPOTIFY INDIA on 17/10/2026.", 199.0, "SPOTIFY INDIA"),
        C("N16", "Your A/C XXXXX1234 has been debited by INR 80.00 on 17-OCT-2026 for UPI txn to RAPIDO. UPI Ref 628900007777", 80.0, "RAPIDO"),
        // Junk guard
        C("N17", "A/c XX9999 debited INR 1,200.00 Dt 17-10-26 thru UPI:628900006666. Bal INR 3,000.00 Not u? Fwd this SMS to 9264092640 to block UPI.-PNB", 1200.0, "Unknown"),
        C("N18", "Rs 70 spent at _CHAI POINT On 2026-10-17:08:00:00 on HDFC Bank Card 1234", 70.0, "CHAI POINT")
    )

    private val unchangedBehaviour = listOf(
        C("G1", "Rs.50.00 debited at Starbucks Coffee", 50.0, "Starbucks Coffee"),
        C("G2", "Rs 200 sent to John Doe via UPI Ref 5029123", 200.0, "John Doe via UPI"),
        C("G3", "Dear UPI user A/C X1234 debited by 1000.00 on date 17Oct26 trf to SHRUTIKIRTI SING Refno 617138114398", 1000.0, "SHRUTIKIRTI SING"),
        C("G4", "ICICI Bank Acct XX123 debited for Rs 164.00 on 27-Jun-26; Amazon Pay Groc credited. UPI:513012345678.", 164.0, "Amazon Pay Groc"),
        C("G5", "Spent Rs.90000 From HDFC Bank Card x4437 At ..SUDHA SILK AND S_ On 2026-01-24:20:30:09 Bal Rs.73283.27 Not You? Call 18002586161/SMS BLOCK DC 4437 to 7308080808", 90000.0, "SUDHA SILK AND S"),
        C("G6", "Rs.587.00 Dr. from A/C XXXXXX8394 and Cr. to delhiverylimited1.rzp@hdfcbank. Ref:123456. AvlBal:Rs10,000.00", 587.0, "delhiverylimited1.rzp@hdfcbank"),
        C("G7", "Spent INR 299\nAxis Bank Card no. XX3768\n28-06-26 08:20:36 IST\nGOOGLEPLAY\nAvl Limit: INR 154392.4", 299.0, "GOOGLEPLAY"),
        C("G8", "Rs.250.00 Dr. from A/C XXXX1234 and Cr. to swiggy@ybl. Ref:123456. AvlBal:Rs5,000", 250.0, "swiggy@ybl")
    )

    private fun check(name: String, cases: List<C>) {
        val failures = cases.mapNotNull { c ->
            val t = SmsParser.parseTransaction(c.sms, emptyList(), cats, oct17)
                ?: return@mapNotNull "${c.id} not recorded: ${c.sms}"
            val merchantOk = t.merchant.equals(c.merchant, true) ||
                (t.merchant.length == 25 && c.merchant.startsWith(t.merchant, true))
            when {
                t.amount != c.amount -> "${c.id} amount ${t.amount}, want ${c.amount}: ${c.sms}"
                !merchantOk -> "${c.id} merchant '${t.merchant}', want '${c.merchant}': ${c.sms}"
                else -> null
            }
        }
        println("[$name] ${cases.size - failures.size}/${cases.size} passed")
        failures.forEach { println("  FAIL $it") }
        assertTrue("${failures.size} of ${cases.size} $name wrong:\n" + failures.joinToString("\n"), failures.isEmpty())
    }

    @Test
    fun bankFormatsAreParsedCorrectly() = check("bank formats", bankFormats)

    @Test
    fun fallbackScenariosAreParsedCorrectly() = check("fallback scenarios", fallbackScenarios)

    @Test
    fun originalFormatsAreUnchanged() = check("unchanged behaviour", unchangedBehaviour)
}
