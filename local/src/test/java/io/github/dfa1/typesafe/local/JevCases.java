package io.github.dfa1.typesafe.local;

import io.github.dfa1.typesafe.core.Content;
import io.github.dfa1.typesafe.core.EvaluateRequest;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/** The requests {@link JevComparison} replays against real JEV and local engines: five suites of
 *  different shapes (text, fields, messages), question mixes and option counts. */
final class JevCases {

    record Case(String suite, int index, EvaluateRequest request) {
        String file() {
            return "%s-%02d.json".formatted(suite, index);
        }
    }

    private JevCases() {
    }

    static List<Case> all() {
        List<Case> result = new ArrayList<>();
        add(result, "support", JevComparison.MESSAGES, m -> JevComparison.request(m));
        add(result, "reviews", REVIEWS, JevCases::review);
        add(result, "moderation", COMMENTS, JevCases::moderation);
        add(result, "email", EMAILS, JevCases::email);
        add(result, "chat", CHATS, JevCases::chat);
        return result;
    }

    private static <T> void add(List<Case> into, String suite, List<T> states, Function<T, EvaluateRequest> request) {
        for (int i = 0; i < states.size(); i++) {
            into.add(new Case(suite, i, request.apply(states.get(i))));
        }
    }

    private static Map<String, String> ordered(String... keyValues) {
        Map<String, String> m = new LinkedHashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            m.put(keyValues[i], keyValues[i + 1]);
        }
        return m;
    }

    // ------------------------------------------------------------------ reviews

    static final List<String> REVIEWS = List.of(
            "Arrived in two days, works perfectly, and half the price of the brand name. Buying another.",
            "The blender died after three weeks. Support never answered my emails.",
            "Decent quality for the money, but the box was crushed and one part was missing.",
            "Absolutely love it. My kids use it every day and it still looks new after a year.",
            "It's fine. Does what it says, nothing more. Probably wouldn't buy again at this price.",
            "Overpriced junk. The paint started peeling the first week.",
            "Customer service was amazing: they replaced the broken unit within 24 hours, no questions asked.",
            "Shipping took a month and tracking never updated, but the product itself is great.",
            "Sound quality is incredible for such a small speaker. Battery life is shorter than advertised.",
            "I returned it. Too heavy, too loud, and the manual is useless.",
            "Five stars. Exactly as described, sturdy, and easy to assemble.",
            "The fabric is thinner than in the photos and it shrank after one wash.",
            "Good value. Not the best I've owned but definitely worth what I paid.",
            "Stopped charging after a month. The replacement had the same problem. Avoid.",
            "Fits perfectly and the color is gorgeous. Got lots of compliments.",
            "The app that comes with it is buggy and keeps logging me out, the hardware is solid though.",
            "Cheap price, cheap feel. You get what you pay for.",
            "Delivery driver left it in the rain, box soaked, but somehow the device still works great.",
            "I've bought this three times now as gifts. Everyone loves it.",
            "Mediocre. The old model was better built and cost less.");

    static EvaluateRequest review(String text) {
        return EvaluateRequest.builder().state(text)
                .noul("recommends", "Would this customer recommend the product to others?")
                .choice("aspect", "Which aspect does the review focus on most?", ordered(
                        "price", "cost, value for money", "quality", "durability, build, how well it works",
                        "shipping", "delivery, packaging", "service", "customer support, returns", "other", ""))
                .score("sentiment", "What is the overall sentiment of the review?",
                        List.of("very negative", "negative", "neutral", "positive", "very positive"))
                .build();
    }

    // ------------------------------------------------------------------ moderation

    static final List<String> COMMENTS = List.of(
            "Great write-up, thanks for sharing the benchmarks!",
            "You're an idiot if you think this is a good idea.",
            "Buy cheap followers now!!! Visit my profile for 90% off, limited time.",
            "I disagree with the conclusion, the sample size seems too small to me.",
            "Nobody asked for your opinion, go back to whatever hole you crawled out of.",
            "Has anyone tried this on Windows? I get a linker error.",
            "Check out my channel for more tutorials like this one, link in bio.",
            "This is the dumbest article I've read all year.",
            "The author clearly has no idea what they're talking about, typical clueless manager.",
            "Thanks! This fixed my problem after two days of searching.",
            "Earn $5000 a week from home, no experience needed, DM me.",
            "Respectfully, I think the second benchmark is flawed because the cache was warm.",
            "People like you are why this community is going downhill.",
            "lol this is so wrong it's funny",
            "Could you share the code for the second example?",
            "Get the best crypto signals here: t.me/fastprofits, 100% guaranteed.",
            "Shut up, nobody cares.",
            "Interesting approach, though I'd use a hash map instead of a list there.",
            "Your code is garbage and so are you.",
            "Is this still relevant for Java 25 or has the API changed?");

    static EvaluateRequest moderation(String text) {
        return EvaluateRequest.builder().state(text)
                .noul("attack", "Does this comment contain a personal attack?", ordered(
                        "true", "it insults or demeans a person, not just their ideas",
                        "false", "it is polite, neutral, or criticizes only ideas or work"))
                .noul("spam", "Is this comment spam or advertising?")
                .choice("action", "What should a moderator do with this comment?", ordered(
                        "allow", "keep it as is", "warn", "keep it but warn the author", "remove", "delete it"))
                .build();
    }

    // ------------------------------------------------------------------ email (structured state)

    private static Map<String, Object> mail(String from, String subject, String body) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("from", from);
        m.put("subject", subject);
        m.put("body", body);
        return m;
    }

    static final List<Map<String, Object>> EMAILS = List.of(
            mail("anna@acme.com", "Sync tomorrow?", "Can we move our 1:1 to 3pm tomorrow? Something came up in the morning."),
            mail("billing@cloudhost.io", "Invoice #4821 is overdue", "Your invoice of $1,240 was due on Sept 15. Please pay to avoid service suspension."),
            mail("news@techweekly.com", "This week in AI", "Top stories: new models, funding rounds and a deep dive into vector databases."),
            mail("mom@family.net", "Sunday lunch", "Are you coming on Sunday? Dad wants to know if he should buy fish."),
            mail("talent@bigcorp.com", "Exciting Staff Engineer role", "Your profile caught our eye. Would you be open to a quick chat about a role on our platform team?"),
            mail("prince@royal-funds.biz", "URGENT business proposal", "I need your help transferring $10M. You will receive 30%. Send your bank details."),
            mail("cto@mycompany.com", "Production incident review", "Please send me your notes on yesterday's outage before Thursday's review."),
            mail("noreply@shop.com", "Your order has shipped", "Order #99812 is on its way and should arrive Friday."),
            mail("paul@acme.com", "Lunch?", "Want to grab lunch today at the usual place?"),
            mail("accounts@supplier.de", "Updated bank details", "Please note our new IBAN for all future payments of our invoices."),
            mail("events@conf.org", "Call for papers closes Friday", "Last chance to submit your talk for JavaConf 2027."),
            mail("hr@mycompany.com", "Please complete the security training", "Mandatory training must be completed by end of month. It takes about 30 minutes."),
            mail("lottery@winner-now.com", "You have WON", "Claim your prize of 1,000,000 EUR by clicking the link and entering your card number."),
            mail("sara@client.com", "Contract renewal", "We'd like to renew for another year but need a revised quote by next week."),
            mail("digest@github.com", "Your weekly digest", "12 new stars, 3 new issues across your repositories."),
            mail("recruiter@startup.io", "Founding engineer?", "We're a seed-stage startup in Zurich looking for a founding engineer. Interested?"),
            mail("bob@friends.org", "Photos from the trip", "Here are the photos from the hike! Let me know which ones you want printed."),
            mail("finance@mycompany.com", "Expense report rejected", "Your expense report is missing receipts for two items. Please resubmit."),
            mail("promo@airline.com", "Flash sale: 40% off", "Book by midnight to save on flights to 120 destinations."),
            mail("ceo@mycompany.com", "All hands moved", "Today's all hands is moved to 4pm. No action needed."));

    static EvaluateRequest email(Map<String, Object> fields) {
        return EvaluateRequest.builder().state(Content.fields(fields))
                .noul("reply", "Does this email need a reply from me?")
                .choice("category", "What kind of email is this?", ordered(
                        "meeting", "scheduling or rescheduling", "invoice", "bills, payments, finance",
                        "newsletter", "news, digests, promotions", "personal", "friends and family",
                        "recruiting", "job offers", "spam", "scams, phishing"))
                .score("priority", "How urgent is this email?", List.of("low", "normal", "high"))
                .build();
    }

    // ------------------------------------------------------------------ chat (messages state)

    static final List<List<String>> CHATS = List.of(
            List.of("customer: my order hasn't arrived", "agent: I see it was delayed, it will arrive tomorrow", "customer: ok thanks!"),
            List.of("customer: I was charged twice", "agent: I've refunded the duplicate charge", "customer: great, I see it now, thank you"),
            List.of("customer: the app crashes on startup", "agent: have you tried reinstalling?", "customer: yes, still crashes", "agent: let me check with the team"),
            List.of("customer: I want a refund, the product is broken", "agent: can you send a photo of the damage?"),
            List.of("customer: how do I export my data?", "agent: Settings > Export > CSV", "customer: found it, thanks"),
            List.of("customer: this is the third time I'm asking, nobody helps me", "agent: I'm sorry, what is the issue?", "customer: my account is locked!!"),
            List.of("customer: can I change my plan?", "agent: sure, which plan would you like?", "customer: the annual one", "agent: done, you're on annual now", "customer: perfect"),
            List.of("customer: your service is terrible", "agent: I'm sorry to hear that, what happened?", "customer: forget it, I'm cancelling"),
            List.of("customer: the API returns 500 since this morning", "agent: we're seeing errors on our side too, engineers are on it"),
            List.of("customer: I can't find the invoice for August", "agent: I've emailed it to you", "customer: got it"),
            List.of("customer: the package arrived damaged", "agent: I'm sorry! I can send a replacement or refund you", "customer: refund please", "agent: refund issued", "customer: thanks"),
            List.of("customer: hi", "agent: hello! how can I help?", "customer: one sec"),
            List.of("customer: the sync feature loses my notes", "agent: which device are you using?", "customer: iPad, latest iOS", "agent: thanks, can you send the app logs?"),
            List.of("customer: is there a student discount?", "agent: yes, 50% with a valid student email", "customer: awesome, signing up now"),
            List.of("customer: I was promised a callback yesterday and nobody called", "agent: apologies, I'll make sure someone calls today", "customer: you said that yesterday"),
            List.of("customer: how do I reset my password?", "agent: click 'forgot password' on the login page", "customer: the email never arrives", "agent: checking our mail logs"),
            List.of("customer: love the new update!", "agent: thank you, glad you like it!"),
            List.of("customer: my data disappeared after the migration", "agent: that's serious, I'm escalating to engineering now"),
            List.of("customer: can I get a refund for the unused months?", "agent: our policy doesn't allow partial refunds", "customer: that's ridiculous"),
            List.of("customer: the dark mode toggle doesn't work", "agent: known bug, fixed in next week's release", "customer: ok, I'll wait"));

    static EvaluateRequest chat(List<String> messages) {
        return EvaluateRequest.builder().state(Content.messages(messages))
                .noul("resolved", "Has the customer's issue been resolved?")
                .choice("next", "What should the support team do next?", ordered(
                        "close", "close the ticket", "escalate", "escalate to engineering",
                        "refund", "offer a refund or compensation", "ask", "ask the customer for more information"))
                .score("satisfaction", "How satisfied does the customer seem?",
                        List.of("very dissatisfied", "dissatisfied", "neutral", "satisfied", "very satisfied"))
                .build();
    }
}
