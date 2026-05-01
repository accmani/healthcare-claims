def forward_to_core_adj(data):
    # Ensure data is HIPAA compliant
    if not is_hipaa_compliant(data):
        raise ValueError('Data is not HIPAA compliant')
    # Forwarding logic here
    pass

# Documentation
# This function forwards data to the Core Adj for final adjudication.
# It checks for HIPAA compliance before proceeding.

# Unit tests
import unittest

class TestForwardToCoreAdj(unittest.TestCase):
    def test_hipaa_compliance(self):
        with self.assertRaises(ValueError):
            forward_to_core_adj(non_compliant_data)

if __name__ == '__main__':
    unittest.main()