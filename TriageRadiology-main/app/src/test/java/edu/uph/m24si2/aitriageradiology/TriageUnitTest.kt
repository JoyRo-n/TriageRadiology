package edu.uph.m24si2.aitriageradiology

import edu.uph.m24si2.aitriageradiology.data.model.TriageRequest
import edu.uph.m24si2.aitriageradiology.data.model.TriageResponse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TriageUnitTest {

    @Test
    fun testTriageRequestDataModel() {
        val request = TriageRequest(
            patientId = "RAD-2026-999",
            patientName = "Jane Doe",
            age = 35,
            gender = "Female",
            modality = "Chest X-Ray"
        )
        assertEquals("RAD-2026-999", request.patientId)
        assertEquals("Jane Doe", request.patientName)
        assertEquals(35, request.age)
        assertEquals("Female", request.gender)
    }

    @Test
    fun testTriageResponseDataModel() {
        val response = TriageResponse(
            status = "success",
            triageCategory = "CRITICAL",
            primaryFinding = "Pneumothorax",
            confidence = 0.95,
            riskScore = 9.5,
            recommendation = "STAT review required"
        )
        assertEquals("success", response.status)
        assertEquals("CRITICAL", response.triageCategory)
        assertEquals("Pneumothorax", response.primaryFinding)
        assertTrue(response.confidence > 0.9)
    }
}
