package com.example.xpense

import com.example.xpense.data.entity.Category
import com.example.xpense.sms.SmsParser
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

/**
 * Reminder / scheduled-debit detection across many bank wordings, in two main scenarios:
 *  1. future, upcoming, scheduled, EMI-due and reminder SMS must be SKIPPED;
 *  2. completed debits must be RECORDED.
 * Plus refunds, credits, OTPs and mandate set-ups, which must also be skipped.
 *
 * Cases are grouped by the review round that produced them (1–4). Every SMS is "received" on
 * 9 Oct 2026 unless stated, so the future-date check is active exactly as on the phone.
 */
class ReminderScenariosTest {
    private val cats = listOf(Category(id = 5, name = "Others", iconName = "Category"))
    private fun at(m: Int, d: Int): Long =
        LocalDate.of(2026, m, d).atStartOfDay(ZoneId.systemDefault()).plusHours(10).toInstant().toEpochMilli()
    private val oct9 = at(10, 9)

    private enum class Kind { FUTURE, REAL, NOT_DEBIT }
    private data class Case(val id: String, val kind: Kind, val sms: String, val received: Long)
    private fun f(id: String, sms: String, rec: Long = oct9) = Case(id, Kind.FUTURE, sms, rec)
    private fun r(id: String, sms: String, rec: Long = oct9) = Case(id, Kind.REAL, sms, rec)
    private fun n(id: String, sms: String, rec: Long = oct9) = Case(id, Kind.NOT_DEBIT, sms, rec)

    // ── Round 1 ─────────────────────────────────────────────────────────────────────────────────
    private val round1 = listOf(
        f("1-F1", "HDFC Bank Loan Ac no. xxx12345 - INSTALLMENT of RS.9731 NACH scheduled for debit on 05-oct-26 from Bank Ac no 12345xxx6789. Please maintain sufficient funds.", at(10, 3)),
        f("1-F2", "Your EMI of Rs.5,400 for Loan A/c XX9012 is due on 10-Oct-26. Please ensure sufficient balance for the debit."),
        f("1-F3", "ICICI Bank: Your AutoPay of Rs 649 for Spotify will be debited on 12-Oct-2026 from Card XX1234."),
        f("1-F4", "Pre-debit intimation: Rs 199.00 will be debited from your a/c XX5678 on 11/10/2026 towards YouTube Premium e-mandate."),
        f("1-F5", "SBI: Standing Instruction for Rs 3000 debit to PPF a/c will be executed on 15-10-2026."),
        f("1-F6", "Dear Customer, your Kotak loan EMI debit of INR 12,500 falls due on 07/10/2026. Kindly keep sufficient funds in your account to avoid penal charges.", at(10, 4)),
        f("1-F7", "Gentle reminder: Your LIC premium of Rs 8,450 for policy 12345678 is due on 28-Oct-2026. Ignore if paid."),
        f("1-F8", "Your Airtel postpaid bill of Rs 799 is generated. Pay by 20-Oct-26 to avoid late fee. Auto debit active."),
        f("1-F9", "Rs 1,500 towards your SIP in Axis Bluechip Fund will be processed on 10-Oct-2026 via NACH debit."),
        f("1-F10", "Your NACH mandate debit of Rs 999 to Bajaj Finance will be presented on 05-11-2026."),
        f("1-F11", "Upcoming debit: INR 2,999 for Amazon Prime annual membership on 15 Oct 2026 from your HDFC Card."),
        f("1-F12", "Your account XX4455 will be charged Rs 499 on 12-Oct-2026 for Netflix subscription."),
        f("1-F13", "Insufficient balance! Your EMI debit of Rs 6,000 on 05-10-2026 could not be processed. Please fund your account.", at(10, 5)),
        f("1-F14", "Your auto debit of Rs.150.00 for Google One is scheduled for 14/10/2026."),
        f("1-F15", "Dear customer, Rs 2,000 will be deducted from A/c XX1234 towards your RD installment on 10th Oct 2026."),
        f("1-F16", "Your credit card XX1234 will be debited for Rs 299 towards Apple Services on 13-Oct-2026."),
        f("1-F17", "Mandate registered: Rs 500 monthly SIP to Zerodha. First debit on 15-Oct-2026."),
        f("1-F18", "EMI Rs 4,567 debit scheduled on 05-Nov-26 for loan XX7788."),
        f("1-F19", "Rs 450 to Swiggy One via debit card XX9988 on 20-Oct-2026"),
        f("1-F20", "Rs 999 for JioHotstar via debit card XX9988 on 05-Oct-2026", at(10, 1)),
        f("1-F21", "Bajaj Finance will debit your account with Rs 3,100 EMI on 02-11-2026."),
        f("1-F22", "Your EMI debit of Rs 2,200 for loan XX1111 will happen tomorrow. Keep enough balance."),
        f("1-F23", "Aapke khate se Rs 1,100 ka EMI debit 12-10-2026 ko hoga. Kripya balance rakhein."),
        f("1-F24", "Rs 4000 SIP via NACH to Groww will be presented on 08-10-26. Avl Bal Rs 2,100. Please maintain sufficient balance.", at(10, 6)),
        f("1-F25", "Your EMI debit of Rs 7,000 is due on 15-10-2026. Ref no LN99881."),

        r("1-R1", "Rs.500.00 debited from A/c **1234 on 09-Oct-26 to VPA swiggy@icici. UPI Ref 123456789012."),
        r("1-R2", "HDFC Bank: Rs 9731.00 debited from a/c XX6789 on 05-10-26 for Loan EMI. Next EMI due on 05-11-26. Avl bal: Rs 12,345.67", at(10, 5)),
        r("1-R3", "Spent INR 299\nAxis Bank Card no. XX3768\n09-10-26 08:20:36 IST\nGOOGLEPLAY\nAvl Limit: INR 154392.4"),
        r("1-R4", "Txn Rs.250.00 On HDFC Bank Card 1234 At SWIGGY by UPI 627812345678 On 09-10"),
        r("1-R5", "Dear Customer, Rs 4,000 has been debited from your account for NACH mandate GROWW on 09-10-2026. Please maintain sufficient balance for future debits."),
        r("1-R6", "Your a/c XX1234 is debited with Rs 1,200.00 on 09-Oct-26 towards SI to PPF. Avl Bal Rs 50,000."),
        r("1-R7", "Rs 150 paid to Zomato via UPI on 09/10/2026. Ref no 1234567."),
        r("1-R8", "Rs. 2,000 withdrawn at ATM SBI on 09-Oct-26. Avl Bal Rs 8,000."),
        r("1-R9", "INR 1,999.00 spent on ICICI Bank Card XX1234 on 09-Oct-26 at AMAZON. Total amount due Rs 15,000 by 25-Oct-26."),
        r("1-R10", "Rs 650 sent to Rahul via UPI on 09-10-26. Ref 112233."),
        r("1-R11", "Rs 5,000 auto-debited from your a/c XX1234 for Bajaj Finance EMI on 05-10-2026.", at(10, 5)),
        r("1-R12", "Rs.250.00 Dr. from A/C XXXX1234 and Cr. to swiggy@ybl. Ref:123456. AvlBal:Rs5,000"),
        r("1-R13", "Payment of Rs 899 to Airtel successful from A/c XX1234 on 09-10-2026. Txn ID 99887766."),
        r("1-R14", "Dear UPI user A/C X1234 debited by 1000.00 on date 09Oct26 trf to AMAZON Refno 627812345678."),
        r("1-R15", "Your account has been debited towards Google for 59.00 on 09/10/2026 (UPI Ref no 123456)."),
        r("1-R16", "Your EMI of Rs 3,250 for loan 778899 has been successfully processed on 09-10-2026."),
        r("1-R17", "Rs 99 deducted for SMS alert charges. This is not a reminder."),

        n("1-N1", "Your A/c X1234 has been credited by Rs 2000.0 on 09-Oct-26."),
        n("1-N2", "123456 is your OTP for txn of Rs 500 at Amazon.")
    )

    // ── Round 2: one SMS per keyword ────────────────────────────────────────────────────────────
    private val round2 = listOf(
        f("2-F1", "Rs 649 for Spotify will be debited from your HDFC Card XX1234 on 12-Oct-26."),
        f("2-F2", "INR 299.00 for Google Play will be auto debited via Axis Bank Credit Card XX3768 by 28-10-26."),
        f("2-F3", "Your Netflix plan Rs 649 will be auto-debited from A/c XX4455 on 14-10-2026."),
        f("2-F4", "Rs 2,000 will be deducted from A/c XX1234 towards your RD installment on 10-Oct-2026."),
        f("2-F5", "Your account XX4455 will be charged Rs 499 on 12-Oct-2026 for Netflix subscription debit."),
        f("2-F6", "Your NACH debit of Rs 999 to Bajaj Finance will be presented on 05-11-2026."),
        f("2-F7", "Your SIP debit of Rs 1,500 in Axis Bluechip Fund will be processed on 10-Oct-2026."),
        f("2-F8", "EMI debit of Rs 4,200 for loan XX5566 will be collected on 11-10-2026."),
        f("2-F9", "Rs 1,250 for your electricity bill will be paid via auto debit on 15-10-2026."),
        f("2-F10", "Rs 3,500 shall be debited from your a/c XX7788 towards loan EMI on 10/10/2026."),
        f("2-F11", "Rs 1,800 is due to be debited from your account XX1122 on 12-Oct-26 for SBI Life."),
        f("2-F12", "Rs 999 is going to be debited from your card XX3344 for YouTube Premium."),
        f("2-F13", "Rs 459 is about to be debited from A/c XX9988 for Swiggy One."),
        f("2-F14", "Rs 2,100 is set to be debited from A/c XX5566 for Bajaj Allianz premium."),
        f("2-F15", "Rs 750 is being debited from your account XX1234 on 11-Oct-26 for Jio AutoPay."),
        f("2-F16", "Bajaj Finance will debit your account with Rs 3,100 EMI on 02-11-2026."),
        f("2-F17", "Rs 5,000 will be transferred from A/c XX1234 to RD a/c on 10-10-2026 as per SI debit."),
        f("2-S1", "Your auto debit of Rs.150.00 for Google One is scheduled for 14/10/2026."),
        f("2-S2", "SI debit of Rs 2500.00 on A/c XX1234 scheduled on 15-10-2026 towards SBI Life."),
        f("2-S3", "A debit of Rs 1,099 is scheduled to happen on 13-Oct-26 for Amazon Prime."),
        f("2-S4", "Your NACH debit of Rs 4,000 to Groww is scheduled. Date 08-10-2026.", at(10, 6)),
        f("2-S5", "EMI Rs 4,567 scheduled as auto debit for loan XX7788."),
        f("2-S6", "EMI Rs 4,567 debit scheduled on 05-Nov-26 for loan XX7788."),
        f("2-S7", "Upcoming debit: INR 2,999 for Amazon Prime annual membership from your HDFC Card."),
        f("2-S8", "Your next debit of Rs 199 for Disney+ Hotstar is on 20-Oct-26."),
        f("2-S9", "Your next EMI of Rs 6,500 for loan XX2233 debit date 05-11-2026."),
        f("2-S10", "Next installment of Rs 1,000 for your RD will debit on 10-11-2026."),
        f("2-S11", "Your next instalment debit Rs 2,750 for Car Loan XX4545."),
        f("2-S12", "Your next payment of Rs 899 for Airtel will be auto debit enabled."),
        f("2-S13", "Loan XX6677: next due Rs 8,000 by debit from A/c XX1234."),
        f("2-S14", "Your EMI debit of Rs.5,400 for Loan A/c XX9012 is due on 10-Oct-26."),
        f("2-S15", "Rs 1,350 debit for Tata Play due by 18-10-2026."),
        f("2-S16", "Rs 2,200 auto debit for ACT Fibernet. Due date 16-10-2026."),
        f("2-S17", "Rs 950 debit due for Vi Postpaid on 22-10-2026."),
        f("2-S18", "Your LIC premium debit of Rs 8,450 for policy 12345678 is due."),
        f("2-S19", "Your Kotak loan EMI debit of INR 12,500 falls due on 07/10/2026.", at(10, 4)),
        f("2-S20", "Rs 3,400 payable on 20-10-2026 by auto debit for HDFC Life."),
        f("2-S21", "Rs 899 debit for Airtel Postpaid payable by 20-10-2026."),
        f("2-S22", "Your EMI debit of Rs 6,000 for loan XX1111 is overdue."),
        f("2-S23", "Rs 2,500 debit for card XX9090 is past due."),
        f("2-W1", "INSTALLMENT of RS.9731 NACH debit on 05-oct-26. Please maintain sufficient funds.", at(10, 3)),
        f("2-W2", "Rs 4,000 SIP debit via NACH on 08-10-2026. Maintain adequate balance.", at(10, 6)),
        f("2-W3", "Rs.750 debit for Jio AutoPay on 11-10-2026. Ensure sufficient balance in A/c XX4455."),
        f("2-W4", "EMI debit of Rs 12,500 on 07-10-2026. Kindly keep sufficient funds.", at(10, 4)),
        f("2-W5", "Your EMI debit of Rs 2,200 for loan XX1111 happens tomorrow. Keep enough balance."),
        f("2-W6", "Your EMI debit of Rs 6,000 on 05-10-2026 could not be processed due to insufficient funds.", at(10, 5)),
        f("2-W7", "NACH debit Rs 1,200 for Bajaj Finserv. Fund your account to avoid bounce charges."),
        f("2-W8", "Rs 799 bill debit for Airtel. Pay early to avoid late fee."),
        f("2-W9", "Rs 5,000 PPF SI debit. Keep funds ready to avoid penalty."),
        f("2-W10", "Rs 3,000 EMI debit for loan XX8899. Kindly fund your account."),
        f("2-M1", "Pre-debit notification: Rs 199.00 towards YouTube Premium e-mandate UMN123 on 11/10/2026."),
        f("2-M2", "Pre-debit intimation for Rs 499 Netflix debit from A/c XX5678."),
        f("2-M3", "Your mandate for Rs 500 debit to Zerodha will run on 15-Oct-2026."),
        f("2-M4", "e-mandate debit Rs 2,999 scheduled for Amazon Prime on 15-10-2026."),
        f("2-M5", "AutoPay debit of Rs 149 for Google One due 14-10-2026."),
        f("2-M6", "Standing Instruction will debit Rs 3000 for PPF a/c on 15-10-2026."),
        f("2-M7", "SI Rs 2,500 debit scheduled for SBI Life on 15-10-2026."),
        f("2-M8", "NACH Rs 999 debit presented to bank for Bajaj Finance on 05-11-2026."),
        f("2-M9", "NACH debit Rs 4,000 Groww. Presentation date 08-10-2026.", at(10, 6)),
        f("2-M10", "Rs 299 Google Play. Please maintain sufficient limit on the card to process the auto debit."),
        f("2-X1", "Reminder: Rs 899 debit for Airtel Postpaid on 20-10-2026."),
        f("2-X2", "Gentle reminder: Rs 8,450 LIC premium debit for policy 12345678."),
        f("2-X3", "Your Airtel bill debit of Rs 799. Pay by 20-Oct-26."),
        f("2-X4", "Rs 1,234 BESCOM bill debit pending. Pay now."),
        f("2-X5", "Rs 2,345 Tata Power bill debit. Pay before 25-10-2026."),
        f("2-X6", "To enjoy policy benefits pay premium of Rs. 1607 online. Auto debit available."),
        f("2-X7", "Premium of Rs. 1607 for policy X0000000 to be paid via debit card. Ignore if paid."),
        f("2-X8", "Your Rs 999 Jio bill debit is pending. Ignore if already paid."),
        f("2-X9", "Your Airtel postpaid bill of Rs 799 is generated. Auto debit active."),
        f("2-X10", "Your HDFC Card statement generated. Rs 23,456 will be auto debit."),
        f("2-X11", "Axis Card XX1234: Total amount due Rs 23,456. Debit via auto pay."),
        f("2-X12", "ICICI Card: Minimum amount due Rs 1,200 for debit on 18-Oct-26."),
        f("2-X13", "SBI Card: min due Rs 650 auto debit."),
        f("2-X14", "Amount due Rs 3,210 for Paytm Postpaid via debit."),
        f("2-X15", "Transaction of Rs 1607.00 for ICICIPru X0000000 has been initiated and will be applied to your policy on due date."),
        f("2-A1", "Rs 4000 SIP via NACH to Groww debit on 08-10-26. Avl Bal Rs 2,100. Please maintain sufficient balance.", at(10, 6)),
        f("2-A2", "Pre-debit notification: Rs 199 YouTube Premium. Avl Bal Rs 5,000."),
        f("2-A3", "Rs 9731 NACH scheduled for debit on 05-10-26. Avl Bal Rs 8,000.", at(10, 3)),
        f("2-A4", "Rs 199 will be debited for YouTube via UPI mandate. UPI Ref 123456."),
        f("2-D1", "Rs 450 to Swiggy One via debit card XX9988 on 20-oct-26"),
        f("2-D2", "Rs 450 to Swiggy One via debit card XX9988 on 20-Oct-2026"),
        f("2-D3", "Rs 450 to Swiggy One via debit card XX9988 on 20Oct26"),
        f("2-D4", "Rs 450 to Swiggy One via debit card XX9988 on 20 Oct 2026"),
        f("2-D5", "Rs 450 to Swiggy One via debit card XX9988 on 20 October 2026"),
        f("2-D6", "Rs 450 to Swiggy One via debit card XX9988 on Oct 20, 2026"),
        f("2-D7", "Rs 450 to Swiggy One via debit card XX9988 on 20/10/2026"),
        f("2-D8", "Rs 450 to Swiggy One via debit card XX9988 on 20/10/26"),
        f("2-D9", "Rs 450 to Swiggy One via debit card XX9988 on 20-10-26"),
        f("2-D10", "Rs 450 to Swiggy One via debit card XX9988 on 20.10.2026"),
        f("2-D11", "Rs 450 to Swiggy One via debit card XX9988 on 2026-10-20"),
        f("2-D12", "Rs 999 for JioHotstar via debit card XX9988 on 05-Oct-2026", at(10, 1)),
        f("2-D13", "Aapke khate se Rs 1,100 ka EMI debit 12-10-2026 ko hoga."),

        r("2-R1", "Rs.500.00 debited from A/c **1234 on 09-Oct-26 to VPA swiggy@icici. UPI Ref 123456789012."),
        r("2-R2", "Rs 99 deducted from A/c XX1234 on 09-10-2026 for SMS alert charges."),
        r("2-R3", "Your card XX4455 was charged Rs 649 at NETFLIX on 09-Oct-26."),
        r("2-R4", "Rs. 2,000 withdrawn at ATM SBI on 09-Oct-26. Avl Bal Rs 8,000."),
        r("2-R5", "Spent INR 299\nAxis Bank Card no. XX3768\n09-10-26 08:20:36 IST\nGOOGLEPLAY\nAvl Limit: INR 154392.4"),
        r("2-R6", "Rs 150 paid to Zomato via UPI on 09/10/2026. Ref no 1234567."),
        r("2-R7", "Rs 650 sent to Rahul via UPI on 09-10-26. Ref 112233."),
        r("2-R8", "Rs 5,000 transferred from A/c XX1234 to RD a/c on 09-10-2026 as per SI."),
        r("2-R9", "Rs 5,000 auto-debited from your a/c XX1234 for Bajaj Finance EMI on 05-10-2026.", at(10, 5)),
        r("2-R10", "Rs.250.00 Dr. from A/C XXXX1234 and Cr. to swiggy@ybl. Ref:123456. AvlBal:Rs5,000"),
        r("2-R11", "Dear UPI user A/C X1234 debited by 1000.00 on date 09Oct26 trf to AMAZON Refno 627812345678."),
        r("2-R12", "Your account has been debited towards Google for 59.00 on 09/10/2026 (UPI Ref no 123456)."),
        r("2-R13", "Txn Rs.250.00 On HDFC Bank Card 1234 At SWIGGY by UPI 627812345678 On 09-10"),
        r("2-R14", "Your EMI of Rs 3,250 for loan 778899 has been successfully processed on 09-10-2026."),
        r("2-R15", "Standing Instruction for Rs 3000 to PPF a/c XX12 was executed on 09-10-2026."),
        r("2-R16", "NACH debit successful: Rs 4,000 to Groww from A/c XX1234 on 09-10-2026."),
        r("2-C1", "Rs 9731.00 debited from a/c XX6789 on 05-10-26 for Loan EMI. Next EMI due on 05-11-26. Avl bal: Rs 12,345.67", at(10, 5)),
        r("2-C2", "Rs 4,000 has been debited from your account for NACH mandate GROWW on 09-10-2026. Please maintain sufficient balance for future debits."),
        r("2-C3", "INR 1,999.00 spent on ICICI Bank Card XX1234 on 09-Oct-26 at AMAZON. Total amount due Rs 15,000 by 25-Oct-26."),
        r("2-C4", "Rs 99 deducted for SMS alert charges. This is not a reminder."),
        r("2-C5", "Rs 1,200 debited for EMI on 09-10-2026. Late fee will be charged if the next EMI bounces."),
        r("2-C6", "Rs 199 debited for YouTube Premium on 09-10-2026. Your upcoming debit is on 09-11-2026."),
        r("2-C7", "Rs 10,000 debited from A/c XX1234 for FD booking. Maturity date 09-10-2027."),
        r("2-C8", "Rs 799 paid to Airtel on 09-10-2026 from A/c XX1234. Next bill pay by 20-11-2026."),

        n("2-N1", "Your A/c X1234 has been credited by Rs 2000.0 on 09-Oct-26."),
        n("2-N2", "123456 is your OTP for txn of Rs 500 at Amazon."),
        n("2-N3", "Refund of Rs 499 for your cancelled EMI has been processed."),
        n("2-N4", "Your payment of Rs 899 to Airtel has been processed. Thank you."),
        n("2-N5", "Refund of Rs 499 for your cancelled NACH debit has been processed.")
    )

    // ── Round 3: different banks and phrasings ──────────────────────────────────────────────────
    private val round3 = listOf(
        f("3-F1", "Dear Customer, EMI of Rs.8,250 for Loan A/c 4567XXXX will be debited from your SB A/c XX2211 on 10/10/2026. Kindly maintain sufficient balance. -SBI"),
        f("3-F2", "ICICI Bank: UPI AutoPay of Rs 199.00 for Netflix will be executed on 12-Oct-26 from A/c XX9087."),
        f("3-F3", "Your e-NACH mandate of Rs 2,500 towards HDFC Mutual Fund is due for execution on 15-10-2026."),
        f("3-F4", "Kotak: Rs 3,999 will be charged to your Credit Card XX4321 for Amazon Prime renewal on 18 Oct 2026."),
        f("3-F5", "Axis Bank: Pre-Debit Notification - Rs 649 for Spotify on 13-10-26 via UPI-Mandate."),
        f("3-F6", "Your SIP of Rs 5,000 in Parag Parikh Flexi Cap will be debited on 11-Oct-2026 via NACH."),
        f("3-F7", "PNB: Your loan installment of Rs 11,200 is due on 15/10/2026. Please keep sufficient balance in your account to avoid penal interest."),
        f("3-F8", "Canara Bank: Standing instruction debit of Rs 1,000 for RD A/c XX2334 will be executed tomorrow."),
        f("3-F9", "HDFC Bank: Your Credit Card XX5566 statement is ready. Total Amt Due: Rs 18,450. Min Amt Due: Rs 920. Due Date: 22-10-2026."),
        f("3-F10", "Reminder: Your Tata AIA premium debit of Rs 12,000 is due on 25-Oct-2026. Pay online to avoid lapse."),
        f("3-F11", "Dear customer, an amount of Rs 450 is scheduled to be debited from your account on 14-10-2026 for Hotstar."),
        f("3-F12", "Your Jio Fiber bill of Rs 1,178 will be auto-paid on 16-10-2026 using your registered UPI AutoPay."),
        f("3-F13", "Yes Bank: Upcoming EMI debit Rs 2,345 for Consumer Durable Loan on 05-Nov-26."),
        f("3-F14", "IDFC FIRST Bank: Rs 999 towards Zee5 annual plan will get debited on 19-10-2026."),
        f("3-F15", "Airtel bill debit of Rs 649 is pending. AutoPay is set for 20-10-2026."),
        f("3-F16", "Bajaj Finserv: EMI debit of Rs 2,120 shall be presented to your bank on 03-Nov-2026. Ensure funds availability."),
        f("3-F17", "SBI Card: Your AutoPay debit for Rs 15,600 is scheduled for 24-10-2026."),
        f("3-F18", "Dear Customer, your debit of Rs 1,500 for SIP is pending execution on 12/10/2026."),
        f("3-F19", "LIC: Premium debit Rs 9,876 for policy 9988776 falls due on 28/10/2026. Kindly pay through LIC app."),
        f("3-F20", "HDFC Bank: Rs 2,000 towards PPF will be transferred via SI on 05-11-26."),
        f("3-F21", "Your Google Play subscription debit of Rs 130 renews on 15-Oct-2026 via card XX1234."),
        f("3-F22", "Netflix: Your membership of Rs 649 will renew on 14-10-2026 and be charged to card XX1234."),
        f("3-F23", "Amount of Rs 3,200 to be auto-debited for Bajaj Allianz on 30-10-2026."),
        f("3-F24", "Gentle Reminder! EMI debit Rs 6,700 for loan no. 55667788 is due tomorrow. Ensure sufficient balance."),
        f("3-F25", "Rs 499 for Disney+ Hotstar will be deducted tomorrow from your HDFC Bank Card."),
        f("3-F26", "Your OneCard bill debit of Rs 4,560 is due in 3 days. Pay now to avoid late charges."),
        f("3-F27", "ICICI Bank: Your loan EMI debit of Rs 9,100 is due today. Please maintain balance."),
        f("3-F28", "Your recurring debit of Rs 299 to YouTube is coming up on 13 Oct 2026."),
        f("3-F29", "Mandate execution of Rs 2,000 debit for Groww SIP on 10-10-2026 from A/c XX5544."),
        f("3-F30", "Kotak: Rs 1,234 debit for Tata Play is upcoming."),
        f("3-F31", "Due amount of Rs 5,500 for loan XX3322 will be collected via NACH on 07-11-2026."),
        f("3-F32", "Axis Bank: Insufficient balance for upcoming NACH debit of Rs 3,000 on 08-10-2026.", at(10, 6)),
        f("3-F33", "Rs 2,499 will be debited on 12-10-2026 for Cult.fit. Avl Bal Rs 1,800."),
        f("3-F34", "Your AutoPay mandate for Rs 99 (Spotify) will next debit on 11-10-2026."),
        f("3-F35", "Aapke account se Rs 799 Airtel bill debit 20-10-2026 ko kaata jayega."),
        f("3-F36", "Union Bank: Rs 1,500 RD instalment debit due 10th Oct."),
        f("3-F37", "Rs.12,000 school fee will be debited via SI on 1st Nov 2026."),
        f("3-F38", "Rs 649 Netflix AutoPay debit tomorrow from card XX1234."),
        f("3-F39", "Your next SIP debit of Rs 3,000 is on 11-10-2026."),
        f("3-F40", "EMI Rs 4,400 for loan XX2211 - debit date 05/11/2026."),
        f("3-F41", "Upcoming payment: Rs 1,999 Swiggy One renewal debit on 16-Oct-26."),
        f("3-F42", "BOB: Your Standing Instruction debit for Rs 5000 is due for execution on 15-10-2026."),
        f("3-F43", "HDFC: Your loan a/c XX7788 has an overdue debit of Rs 9,731. Pay immediately to avoid charges."),
        f("3-F44", "Your SBI Life premium debit of Rs 25,000 is payable on 01-11-2026."),
        f("3-F45", "Rs 3,000 will be withdrawn from your account for RD on 10-10-2026."),
        f("3-F46", "Card XX1234 AutoPay debit Rs 2,999 Amazon Prime - charge date 15-10-2026."),
        f("3-F47", "Your SIP instalment debit of Rs 1,000 for Nippon India Fund is due on 12/10/2026."),
        f("3-F48", "Dear Customer, Rs 5,600 NACH debit request from Tata Capital received. Will be processed on 08-Nov-2026."),
        f("3-F49", "Rs 1,099 debit for Amazon Prime on 15-10-2026. Please ensure your card has sufficient limit."),
        f("3-F50", "Your BESCOM bill of Rs 1,876 is generated. It will be auto debited on 20-10-2026."),

        r("3-R1", "SBI: Rs 500.00 debited from A/c XX4421 on 09Oct26 by UPI ref 628112345678. If not you, call 1800111109."),
        r("3-R2", "HDFC Bank: Rs.1,250.00 debited from a/c **1234 on 09-10-26 to VPA zomato@hdfcbank (UPI Ref No 628212345678). Not you? Call 18002586161"),
        r("3-R3", "ICICI Bank Acct XX123 debited for Rs 2,499.00 on 09-Oct-26; Amazon credited. UPI:628312345678. Call 18002662 for dispute."),
        r("3-R4", "Kotak Bank: Sent Rs 300.00 from Kotak Bank AC X1234 to rahul@okaxis on 09-10-26. UPI Ref 628412345678."),
        r("3-R5", "Axis Bank: INR 1,999.00 spent on credit card no. XX9876 on 09-10-26 at MYNTRA. Avl Lmt INR 85,000."),
        r("3-R6", "Your A/c XX1234 is debited for Rs 9,731.00 on 05-10-2026 towards HDFC LTD LOAN EMI (NACH). Avl Bal Rs 22,100.50", at(10, 5)),
        r("3-R7", "PNB: A/c XX5678 debited INR 3,000.00 on 09-10-26 for ATM WDL at PNB ATM DELHI. Avl Bal INR 12,450.00"),
        r("3-R8", "Canara Bank: Rs 1,000 paid from A/c XX3344 to IRCTC on 09-10-2026. UPI Ref 628512345678."),
        r("3-R9", "IDFC FIRST Bank: Your A/c XX7788 has been debited with INR 499.00 for Netflix (UPI AutoPay) on 09-10-2026."),
        r("3-R10", "Yes Bank: INR 2,000 withdrawn from A/c XX2233 at ATM on 09-10-26. Avl Bal INR 5,600."),
        r("3-R11", "IndusInd: Rs 15,000 transferred via IMPS from A/c XX1122 to Suresh on 09-10-2026. Ref 628612345678."),
        r("3-R12", "Your SBI Debit Card XX4455 was used for a purchase of Rs 850.00 at DMART on 09-10-26."),
        r("3-R13", "HDFC Bank Card XX1234 used for Rs 1,560 at UBER on 09-10-26. Avl Lmt Rs 45,000."),
        r("3-R14", "NEFT of Rs 25,000 debited from A/c XX9988 to ABC Pvt Ltd on 09-10-2026. UTR SBIN1234567."),
        r("3-R15", "ECS debit of Rs 2,100 for LIC processed from A/c XX6677 on 09-10-2026."),
        r("3-R16", "BOI: Rs 4000.00 Debited (NACH) - NACH DR INW - GROWW INVEST TEC on 09-10-2026. Avl Bal 32,902.85"),
        r("3-R17", "Dear Customer, your UPI AutoPay of Rs 199 to Netflix was successful on 09-10-2026. Ref 628712345678."),
        r("3-R18", "SIP of Rs 5,000 executed successfully via NACH from A/c XX1234 on 09-10-2026."),
        r("3-R19", "Rs 649 debited from card XX1234 for Netflix. Next renewal on 09-11-2026."),
        r("3-R20", "Rs 9,731 EMI debited for loan XX12345. Please maintain sufficient funds for next EMI due on 05-11-26."),
        r("3-R21", "Txn of INR 120.00 done on Card XX9090 at STARBUCKS on 09-Oct-26. Avl Lmt INR 1,20,000."),
        r("3-R22", "Paid Rs 45 to Chai Point via PhonePe on 09-10-26 from A/c XX1234."),
        r("3-R23", "Money sent! Rs 2,000 to Mom from A/c XX1234 on 09-10-26. UPI Ref 628812345678."),
        r("3-R24", "Rs 3,250 deducted towards Bajaj Finance EMI from A/c XX2222 on 09-10-2026."),
        r("3-R25", "POS txn of Rs 2,340 at RELIANCE SMART on HDFC Debit Card XX1234 on 09-10-26."),
        r("3-R26", "Rs 100 charged as SMS alert fee on A/c XX1234 for Q3."),
        r("3-R27", "Your A/c XX4567 debited by Rs 799.00 on 09-10-2026 towards Airtel bill payment via BBPS."),
        r("3-R28", "A/c XX1234 Debited Rs.1,000.00 for SI to PPF on 09-10-2026. Bal Rs.45,000."),
        r("3-R29", "Rs 699 debited from your account on 09-10-26 for Amazon Prime auto-renewal. Your next auto debit will be on 09-10-27."),
        r("3-R30", "Rs.2,500.00 Dr. from A/C XXXX5566 and Cr. to amazon@apl. Ref:628912345678. AvlBal:Rs10,200"),
        r("3-R31", "Rs 1,200 spent using ICICI Card XX5544 at SWIGGY on 09-Oct-26. Total due Rs 25,600, due by 25-Oct-26."),
        r("3-R32", "Your EMI of Rs 5,400 for Loan XX9012 has been processed successfully on 09-10-2026."),
        r("3-R33", "Your Standing Instruction of Rs 3,000 for PPF was executed on 09-10-2026."),
        r("3-R34", "NACH debit successful for Rs 2,000 towards Groww on 09-10-26."),
        r("3-R35", "Debit alert: Rs 349 at Spotify India on card XX1234, 09-10-26 13:22."),
        r("3-R36", "UPI txn: Rs 80 paid to Metro Card Recharge on 09-10-2026."),
        r("3-R37", "Rs 50,000 debited for FD XX7788, maturity on 09-10-2027."),
        r("3-R38", "Rs 500 debited from A/c XX1234 on 01-10-2026 to swiggy@icici", at(10, 1)),
        r("3-R39", "Rs 2,000 debited from A/c XX1234 on 09-10-26 for Bajaj EMI. Bounce charges will be levied if insufficient funds next time."),
        r("3-R40", "INR 5,000.00 has been debited from your account XX5566 via e-mandate towards Zerodha on 09-10-2026."),

        n("3-N1", "Rs 5,000 credited to your A/c XX1234 on 09-10-26 by NEFT from ABC Corp."),
        n("3-N2", "Refund of Rs 799 for order #12345 has been initiated and will be credited in 5-7 days."),
        n("3-N3", "Your Swiggy order refund of Rs 249 has been credited to your UPI account."),
        n("3-N4", "Txn of Rs 1,200 at Flipkart on card XX1234 has been reversed."),
        n("3-N5", "Your OTP for transaction of Rs 4,500 at Amazon is 482910."),
        n("3-N6", "Cashback of Rs 50 credited for your Paytm transaction."),
        n("3-N7", "Payment of Rs 1,999 to Amazon failed. Amount, if debited, will be refunded in 3 days."),
        n("3-N8", "Your UPI AutoPay mandate of Rs 199 for Netflix has been created successfully."),
        n("3-N9", "Your payment of Rs 1,607 for your iPru policy has been successfully debited. Team Policybazaar"),
        n("3-N10", "Recharge of INR 239 is successful for your Jio number 98XXXXXX12."),
        n("3-N11", "Your UPI AutoPay mandate of Rs 199 for Netflix registration is successful.")
    )

    // ── Round 4: fresh wordings (would/getting/renewed/last date/expected/soon …) ───────────────
    private val round4 = listOf(
        f("4-F1", "HDFC Bank: EMI of Rs 7,450 for loan XX8899 would be debited from your account on 12-10-2026."),
        f("4-F2", "Rs 2,300 is getting debited from A/c XX1234 for LIC premium on 14-10-2026."),
        f("4-F3", "Kotak: Please clear Rs 5,670 dues by debit on or before 20-10-2026."),
        f("4-F4", "Your EMI debit of Rs 3,100 for loan XX4545 - last date 15-10-2026."),
        f("4-F5", "Advance intimation: NACH debit of Rs 6,000 for Tata Capital on 07-11-2026."),
        f("4-F6", "Your Amazon Prime membership of Rs 1,499 will be renewed on 18-10-2026 and charged to card XX1234."),
        f("4-F7", "Next billing date 21-10-2026: Rs 199 will be auto debited from card XX5678 for Spotify."),
        f("4-F8", "EMI Reminder: Rs 4,250 for Two Wheeler Loan XX1122 will be presented on 05-11-2026."),
        f("4-F9", "Your Tata Play subscription debit of Rs 350 is due for renewal on 17-10-2026."),
        f("4-F10", "Rs 1,200 SIP debit in HDFC Flexi Cap is scheduled on 11-10-2026 via e-mandate."),
        f("4-F11", "SBI: Your SI for Rs 2,000 to PPF is pending and will be executed on 15-10-2026."),
        f("4-F12", "Your loan EMI debit of Rs 8,900 will hit your account on 05-11-2026. Keep funds ready."),
        f("4-F13", "Auto-renewal on 16-10-2026: Rs 2,999 debit for Microsoft 365."),
        f("4-F14", "Your upcoming bill debit of Rs 1,450 for Vi Postpaid will be auto-paid."),
        f("4-F15", "Payment due: Rs 899 debit for Airtel Xstream on 19-10-2026."),
        f("4-F16", "Rs 3,600 debit for Star Health premium due on 30-10-2026. Kindly ensure sufficient funds."),
        f("4-F17", "Your EMI debit Rs 2,999 is unpaid. Please pay immediately to avoid penal charges."),
        f("4-F18", "Dear Customer, your NACH debit of Rs 4,000 to Groww will be initiated on 08-10-2026.", at(10, 6)),
        f("4-F19", "Rs.5,000 debit for RD XX7788 to be processed tomorrow."),
        f("4-F20", "We will debit Rs 999 from your card XX1234 for Hotstar on 14-10-2026."),
        f("4-F21", "Your recurring debit of Rs 149 for iCloud is due next week."),
        f("4-F22", "ICICI: Rs 12,450 auto debit for your loan will happen on 22-10-2026 from A/c XX3344."),
        f("4-F23", "Insurance premium debit Rs 18,000 for HDFC Ergo falls due tomorrow."),
        f("4-F24", "Your debit card XX4455 will be charged Rs 1,299 for Zomato Gold on renewal."),
        f("4-F25", "Rs 2,750 for your gym membership debit is due on the 15th."),
        f("4-F26", "Your SIP debit of Rs 500 will be made on 12-10-2026."),
        f("4-F27", "Debit of Rs 6,600 towards Home Loan EMI expected on 05-11-2026."),
        f("4-F28", "Rs 450 electricity bill debit will be paid automatically on 20-10-2026."),
        f("4-F29", "Mandate alert: Rs 99 will be debited next month for Spotify."),
        f("4-F30", "Your Jio postpaid bill debit of Rs 599 is generated and due on 25-10-2026."),
        f("4-F31", "Rs 1,999 Swiggy One auto-renewal debit soon from card XX1234."),
        f("4-F32", "Your EMI debit of Rs 4,000 is coming up on 05-11-2026."),
        f("4-F33", "Heads up! Rs 649 for Netflix will be charged on 14-10-2026."),
        f("4-F34", "Kindly arrange funds for EMI debit of Rs 5,200 on 10-10-2026."),
        f("4-F35", "Dear customer, Rs 300 SIP debit will be processed on the next business day."),

        r("4-R1", "Rs 7,450 debited from A/c XX1234 on 12-10-26 for HDFC loan EMI. Avl Bal Rs 18,200", at(10, 12)),
        r("4-R2", "LIC premium Rs 2,300 has been debited from your A/c XX1234 on 09-10-2026."),
        r("4-R3", "Dear Customer, Rs 1,499 charged on card XX1234 for Amazon Prime renewal on 09-10-26."),
        r("4-R4", "Your Amazon Prime membership was renewed and Rs 1,499 charged to card XX1234 on 09-10-2026."),
        r("4-R5", "NACH: Rs 6,000 debited to Tata Capital from A/c XX5566 on 09-10-2026. Next EMI on 09-11-2026."),
        r("4-R6", "Rs 199 auto debited from card XX5678 for Spotify on 09-10-26."),
        r("4-R7", "EMI of Rs 4,250 for loan XX1122 deducted from A/c XX3344 on 09-10-2026."),
        r("4-R8", "Your SIP of Rs 1,200 in HDFC Flexi Cap has been debited via e-mandate on 09-10-2026."),
        r("4-R9", "SBI: SI for Rs 2,000 to PPF executed successfully on 09-10-2026."),
        r("4-R10", "IMPS: Rs 10,000 debited from A/c XX1234 to Ravi (Ref 628912345000) on 09-10-26."),
        r("4-R11", "Purchase of Rs 3,499 at CROMA on HDFC Card XX1234 on 09-10-26. Avl Lmt Rs 60,000."),
        r("4-R12", "Transaction of Rs 650 at DOMINOS on ICICI Card XX5544 on 09-10-2026 approved."),
        r("4-R13", "Bill paid: Rs 899 to Airtel Xstream from A/c XX1234 on 09-10-2026."),
        r("4-R14", "Rs 3,600 Star Health premium auto-debit successful from A/c XX1234 on 09-10-2026."),
        r("4-R15", "Your EMI of Rs 2,999 has been paid successfully from A/c XX4455."),
        r("4-R16", "Rs 999 paid to Hotstar via UPI AutoPay on 09-10-2026. Next debit on 09-11-2026."),
        r("4-R17", "Rs 18,000 debited for HDFC Ergo premium from A/c XX1234 on 09-10-2026."),
        r("4-R18", "Rs 1,299 spent on debit card XX4455 at ZOMATO on 09-10-26."),
        r("4-R19", "Rs 500 SIP debited on 09-10-2026. Your next SIP is scheduled for 09-11-2026."),
        r("4-R20", "Home Loan EMI Rs 6,600 debited on 05-11-2026 from A/c XX1234.", at(11, 5)),
        r("4-R21", "Rs 450 electricity bill paid automatically from A/c XX1234 on 09-10-2026."),
        r("4-R22", "Debited Rs 99 for Spotify mandate from A/c XX1234 on 09-10-26."),
        r("4-R23", "Rs 599 Jio postpaid bill debited from A/c XX1234 on 09-10-2026. Next due on 09-11-2026."),
        r("4-R24", "Your card XX1234 has been charged Rs 649 for Netflix on 09-10-2026."),
        r("4-R25", "Rs 5,200 EMI deducted from A/c XX1234 on 09-10-2026. Thank you for timely payment."),
        r("4-R26", "UPI: You paid Rs 120 to Rapido on 09-10-26. UPI Ref 628900001111."),
        r("4-R27", "A/c XX1234 debited Rs 300 for SIP on 09-10-2026; mandate will continue monthly."),
        r("4-R28", "Rs 2,000 sent via NEFT to LIC from A/c XX1234 on 09-10-2026."),
        r("4-R29", "Withdrawal of Rs 4,000 at ICICI ATM from A/c XX1234 on 09-10-26. Avl Bal Rs 9,000."),
        r("4-R30", "Rs 1,999 debited for Swiggy One auto-renewal from card XX1234 on 09-10-2026.")
    )

    private val rounds = listOf("Round 1" to round1, "Round 2" to round2, "Round 3" to round3, "Round 4" to round4)

    private fun check(kind: Kind, expectRecord: Boolean) {
        val lines = mutableListOf<String>()
        val failures = mutableListOf<String>()
        for ((name, cases) in rounds) {
            val ofKind = cases.filter { it.kind == kind }
            val wrong = ofKind.filter { c ->
                (SmsParser.parseTransaction(c.sms, emptyList(), cats, c.received) != null) != expectRecord
            }
            lines += "$name: ${ofKind.size - wrong.size}/${ofKind.size} passed"
            failures += wrong.map { "${it.id} expected ${if (expectRecord) "RECORD" else "SKIP"}: ${it.sms}" }
        }
        println("[$kind] " + lines.joinToString(" | "))
        failures.forEach { println("  FAIL $it") }
        assertTrue("${failures.size} $kind scenarios wrong:\n" + failures.joinToString("\n"), failures.isEmpty())
    }

    @Test
    fun scenario1_futureRemindersAndScheduledDebitsAreSkipped() = check(Kind.FUTURE, expectRecord = false)

    @Test
    fun scenario2_realDebitsAreRecorded() = check(Kind.REAL, expectRecord = true)

    @Test
    fun nonDebitsAreSkipped() = check(Kind.NOT_DEBIT, expectRecord = false)
}
