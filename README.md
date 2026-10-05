# FoodTrack — Android app

## מה יש כאן עכשיו
לא עוד שלד בודד — יש כאן לולאת שימוש מלאה: חיפוש מזון (בסיסי + אישי),
הוספה ליומן היום עם חישוב גרמים, מסך "היום" עם פס התקדמות קלוריות,
סיכום מאקרו, יומן נוזלים ומשקל.

### מסכים
- **TodayActivity** (מסך הפתיחה) — תאריך, פס התקדמות קלוריות מול יעד יומי
  (לחיצה על המספר עצמו פותחת דיאלוג לעריכת היעד), סיכום מאקרו יומי,
  מונה כוסות מים (+1/-1), הזנת משקל מהירה + המשקל האחרון שנרשם, רשימת
  יומן היום עם מחיקה, וכפתור צף (+) שפותח את מסך החיפוש.
- **MainActivity** (חיפוש) — מחפש **גם** במאגר הבסיס (`app_foods.db`)
  **וגם** במזון האישי שהמשתמש יצר, ומאחד לרשימה אחת (מזון אישי קודם).
  כפתור "+ הוסף מזון אישי" פותח את מסך היצירה.
- **AddLogEntryActivity** — מוצג אחרי בחירת מזון: הזנת גרמים (ברירת מחדל
  100), תצוגה חיה של הקלוריות/מאקרו לפי הכמות שהוזנה, וכפתור מהיר
  להזנת "מנת בית" ידועה אם קיימת במאגר (למשל "כף — 15 גרם").
- **CustomFoodActivity** — טופס ליצירת מזון אישי (שם + ערכים ל-100 גרם),
  שאחרי השמירה מיד ניתן לחפש ולהוסיף ליומן כמו כל מזון אחר.

### שכבת נתונים
שני מסדי SQLite **נפרדים לגמרי** (חשוב שיישאר כך):
- `AppDatabase` (`app_foods.db`) — **read-only**, מוטמע כ-asset. **המקור
  הוחלף מ-USDA ל-Tzameret** (משרד הבריאות, data.gov.il) — 4,510 מאכלים,
  עברית טבעית מלכתחילה (אין יותר צורך בצינור התרגום שבנינו ל-USDA),
  כולל מותגים ישראליים, ו-18,876 מנות בית (טבלת `food_portions` נפרדת,
  one-to-many — לא עוד עמודת מנה בודדת). קטגוריה (`category_he`)
  nullable — ל-Tzameret אין טור קטגוריה מובנה כמו שהיה ל-USDA.
- `UserDatabase` (`user_data.db`) — **read-write**, נוצר על המכשיר.
  8 טבלאות: `custom_foods`, `custom_food_portions` (סכימה מוכנה, אין
  עדיין UI למלא), `log_entries` (עם ערכים "מוקפאים" בזמן ההוספה —
  snapshot, לא per-100g, ועם עמודת `mealType` שמורה לעתיד), `favorite_foods`
  (סכימה מוכנה, אין UI), `meal_templates` + `meal_template_items` (סכימה
  מוכנה, אין UI), `weight_log`, `water_log`.

הפרדה זו קריטית: עדכון עתידי של `app_foods.db` (כשעוד קטגוריות
יתורגמו) לא נוגע בכלל ב-`user_data.db` — אין סיכון לאבד נתוני משתמש.

### DEBUGGING SCAFFOLDING — קיים בכוונה
כל המסכים יורשים מ-`BaseActivity`, שתופסת כל קריסה (גם סינכרונית וגם
בתוך coroutines דרך `runSafely`) ומציגה אותה כדיאלוג טקסט מלא על המסך —
כולל קריסה שקרתה בהפעלה הקודמת, שנשמרת לקובץ ומוצגת אוטומטית בפתיחה
הבאה (`FoodTrackApplication`). זה נבנה במקור כי אין גישה ל-Logcat בסביבת
הפיתוח. **אל תסירו את זה** עד שיהיה מנגנון לוגים/דיווח קריסות אמיתי.

## מה **לא** בנוי עדיין (הוחלט במפורש לדחות, לא נשכח)
- **תבניות ארוחה** ("ארוחת בוקר טיפוסית" בלחיצה אחת) — מהתוכנית
  המקורית, נדחה כי דורש UI נוסף משמעותי (בחירת כמה פריטים, שמירה,
  שכפול ליומן). ה-DB לא כולל עדיין טבלאות לזה.
- **רשימת "מזונות אחרונים/מועדפים"** בתור קיצור דרך על מסך החיפוש —
  יש כבר שאילתה מוכנה ל-DAO (`LogDao.recentDistinctFoods`) אבל אין
  לה UI. הבעיה שצריך לפתור: הרשומה ביומן שומרת כמות "קפואה" (למשל
  "150 גרם"), לא ערך ל-100 גרם — צריך JOIN חזרה למקור (בסיס/אישי)
  כדי להציג נכון ברשימת קיצורי דרך.
- **סטטיסטיקות שבוע/חודש** — יש רק "היום". Query-ים לשבוע/חודש עוד
  לא נכתבו.
- **ייצוא/גיבוי** (CSV, PDF, גיבוי-שחזור) — לא נבנה כלל.
- **מגבלת התאמה למסכים קטנים (3")** — הלייאאוטים פשוטים ו-scrollable
  אז סביר שיעבדו, אבל לא נבדק בפועל במסך כזה.

## הרצה
פתח ב-Android Studio, סנכרן Gradle, הרץ. **זכרו**: appcompat/material
נעוצים בגרסאות ישנות בכוונה (תמיכה ב-API 19) — אל תשדרגו בלי לבדוק
release notes קודם (ראו הערה ב-`app/build.gradle.kts`).

**עדיין לא הרצתי build אמיתי בעצמי** — אין לי סביבת Android SDK כאן.
בדקתי סטטית: כל ה-XML תקין, כל ה-view bindings תואמים, כל ה-brace/paren
מאוזנים, וסכימת ה-DB המוטמע תואמת בדיוק למה ש-Room מצפה (fdc_id NOT
NULL + שני האינדקסים המוצהרים ב-`FoodEntity`). זה לא תחליף לקומפילציה
אמיתית — אם תתקלו בשגיאה, הדביקו אותה כמו קודם.

## Physical activity / 2024 Adult Compendium

FoodTrack now includes an offline physical-activity calculator backed by the **2024 Adult Compendium of Physical Activities**. The bundled `physical_activities_2024.tsv` contains the 2024 activity codes, MET values, categories, and descriptions, so no network connection is required at runtime.

Estimated activity energy expenditure uses the standard MET equation:
`kcal = MET × 3.5 × body_weight_kg × minutes / 200`.

The app labels this as an estimate; it is not a measurement from a wearable or metabolic test.

Source and attribution: https://pacompendium.com/ . The Compendium site states that the Adult Compendium is free to use for commercial purposes and asks users to cite the Compendium website/publications.

## מנוע חיפוש המאכלים (חבילת `search`)
החיפוש רץ בזיכרון על כל מאגר המאכלים (ולא ב-`LIKE` של SQL): נרמול עברית (גרש, ניקוד, אותיות סופיות),
יחיד/רבים, תחיליות ה/ב/ל, כתיב מלא/חסר, מילים נרדפות (`SearchSynonyms`), תיקון שגיאות הקלדה והקלדה במקלדת אנגלית.
סדר הדירוג: התאמה מדויקת של המילה כפי שהוקלדה, בשם הראשי של המאכל, קודמת לצורות נגזרות (רבים/תחילית/תיקון);
אחריה מאכלים "טיפוסיים" (מילות הכנה נפוצות) לפני מילים נדירות; מועדפים, מאכלים שנרשמו הרבה ביומן ומזון אישי מקבלים בונוס.
בדיקות: `app/src/test/.../search`.


<!-- CI build probe -->
